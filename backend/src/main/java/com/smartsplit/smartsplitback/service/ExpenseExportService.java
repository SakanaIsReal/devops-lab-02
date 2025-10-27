package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.repository.ExpenseItemRepository;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExpenseExportService {

    private final ExpenseRepository expenses;
    private final ExpenseItemRepository itemsRepo;
    private final ExpenseItemShareRepository sharesRepo;
    private final ExpensePaymentRepository paymentsRepo;
    private final ExchangeRateService fx;

    private final TemplateEngine engine;

    public ExpenseExportService(ExpenseRepository expenses,
                                ExpenseItemRepository itemsRepo,
                                ExpenseItemShareRepository sharesRepo,
                                ExpensePaymentRepository paymentsRepo,
                                ExchangeRateService fx) {
        this.expenses = expenses;
        this.itemsRepo = itemsRepo;
        this.sharesRepo = sharesRepo;
        this.paymentsRepo = paymentsRepo;
        this.fx = fx;

        // Configure Thymeleaf (template from classpath:/templates/)
        ClassLoaderTemplateResolver r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setCharacterEncoding("UTF-8");
        r.setTemplateMode("HTML");
        r.setCacheable(false);
        this.engine = new TemplateEngine();
        this.engine.setTemplateResolver(r);
    }

    public byte[] renderExpensePdf(Long expenseId) {
        Expense e = expenses.findById(expenseId).orElse(null);
        if (e == null) throw new NoSuchElementException("Expense not found");

        var items = itemsRepo.findByExpense_Id(expenseId);
        var shares = sharesRepo.findByExpenseId(expenseId);
        var payments = paymentsRepo.findByExpense_Id(expenseId);

        // Formatters
        NumberFormat money = NumberFormat.getNumberInstance(Locale.US);
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // เรทแปลงเป็น THB (ใช้ custom จาก exchangeRatesJson ถ้ามี)
        Map<String, BigDecimal> rates = fx.getRatesToThb(e);

        // ==== Items: แสดงทั้ง original และ THB ====
        List<Map<String, Object>> itemVM = new ArrayList<>();
        BigDecimal itemsTotalThb = BigDecimal.ZERO;

        for (ExpenseItem it : items) {
            String ccy = safeUpper(getItemCurrency(it));
            BigDecimal original = nvl(it.getAmount());
            BigDecimal thb = fx.toThb(ccy, original, rates);

            itemsTotalThb = itemsTotalThb.add(thb);

            itemVM.add(Map.of(
                    "name", it.getName(),
                    "amountOriginalFmt", (original != null ? money.format(original) : "-") + (ccy != null ? " " + ccy : ""),
                    "amountThbFmt", money.format(thb)
            ));
        }

        // ==== Payments: ถือเป็น THB ตามโมเดลเดิม ====
        List<Map<String, Object>> payVM = new ArrayList<>();
        BigDecimal verifiedTotal = BigDecimal.ZERO;
        for (ExpensePayment p : payments) {
            if (p.getStatus() == PaymentStatus.VERIFIED && p.getAmount() != null) {
                verifiedTotal = verifiedTotal.add(p.getAmount());
            }
            payVM.add(Map.of(
                    "by", p.getFromUser().getUserName(),
                    "amountThbFmt", money.format(nvl(p.getAmount())),
                    "status", p.getStatus().name()
            ));
        }

        // ==== Shares: ใช้ shareOriginalValue (สกุลตาม item) + shareValue (THB) ====
        List<Map<String, Object>> shareVM = new ArrayList<>();
        for (ExpenseItemShare s : shares) {
            String ccy = null;
            if (s.getExpenseItem() != null) {
                ccy = safeUpper(getItemCurrency(s.getExpenseItem()));
            }
            String percentFmt = (s.getSharePercent() != null)
                    ? (s.getSharePercent().stripTrailingZeros().toPlainString() + "%")
                    : "-";

            shareVM.add(Map.of(
                    "participant", s.getParticipant().getUserName(),
                    "valueOriginalFmt", (s.getShareOriginalValue() != null ? money.format(s.getShareOriginalValue()) : "-")
                            + (ccy != null ? " " + ccy : ""),
                    "valueThbFmt", s.getShareValue() != null ? money.format(s.getShareValue()) : "-",
                    "percentFmt", percentFmt
            ));
        }

        BigDecimal outstanding = itemsTotalThb.subtract(verifiedTotal);

        // Build model for template
        Context ctx = new Context(Locale.forLanguageTag("th"));
        ctx.setVariable("e", Map.of(
                "title", e.getTitle(),
                "type", e.getType(),
                "status", e.getStatus()
        ));
        ctx.setVariable("groupName", e.getGroup().getName());
        ctx.setVariable("payerName", e.getPayer().getUserName());
        ctx.setVariable("amountFmt", e.getAmount() != null ? money.format(e.getAmount()) : "-");
        ctx.setVariable("createdAtStr", e.getCreatedAt() != null ? e.getCreatedAt().format(dtf) : "-");
        ctx.setVariable("nowStr", dtf.format(java.time.LocalDateTime.now()));

        // view models
        ctx.setVariable("items", itemVM);
        ctx.setVariable("itemsTotalThbFmt", money.format(itemsTotalThb));

        ctx.setVariable("shares", shareVM);

        ctx.setVariable("payments", payVM);
        ctx.setVariable("verifiedTotalFmt", money.format(verifiedTotal));
        ctx.setVariable("outstandingFmt", money.format(outstanding));

        // Render HTML
        String html = engine.process("pdf/expense", ctx);
        html = html.replace("\uFEFF", "").trim();

        // HTML -> W3C DOM
        org.jsoup.nodes.Document jsoup = Jsoup.parse(html);
        jsoup.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.html);
        W3CDom w3cDom = new W3CDom();
        Document w3c = w3cDom.fromJsoup(jsoup);

        // Render PDF
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();

            // โหลดฟอนต์แบบ supplier
            b.useFont(() -> getClass().getResourceAsStream("/fonts/NotoSansThai-Regular.ttf"),
                    "NotoSansThai", 400, PdfRendererBuilder.FontStyle.NORMAL, true);
            b.useFont(() -> getClass().getResourceAsStream("/fonts/NotoSansThai-Bold.ttf"),
                    "NotoSansThai", 700, PdfRendererBuilder.FontStyle.NORMAL, true);

            if (getClass().getResource("/fonts/NotoSansThai-Regular.ttf") == null
                    || getClass().getResource("/fonts/NotoSansThai-Bold.ttf") == null) {
                throw new IllegalStateException("Font files not found in classpath:/fonts/");
            }

            b.withW3cDocument(w3c, null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to render PDF", ex);
        }
    }


    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String getItemCurrency(ExpenseItem it) {
        try {
            var m = ExpenseItem.class.getMethod("getCurrency");
            Object r = m.invoke(it);
            if (r == null) return "THB";
            String s = String.valueOf(r);
            return s == null || s.isBlank() ? "THB" : s;
        } catch (Exception ignore) {
            return "THB";
        }
    }

    private String safeUpper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
