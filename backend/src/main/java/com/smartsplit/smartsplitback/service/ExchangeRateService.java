package com.smartsplit.smartsplitback.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.Expense;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ExchangeRateService {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final int OUT_SCALE = 6;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    public Map<String, BigDecimal> getRatesToThb(Expense expense) {
        String json = expense.getExchangeRatesJson();
        if (json != null && !json.isBlank()) {
            try {
                JsonNode node = om.readTree(json);
                Map<String, BigDecimal> map = new HashMap<>();
                Iterator<String> it = node.fieldNames();
                while (it.hasNext()) {
                    String ccy = it.next().toUpperCase(Locale.ROOT);
                    BigDecimal v = node.get(ccy).decimalValue();
                    map.put(ccy, v);
                }
                map.putIfAbsent("THB", BigDecimal.ONE);
                return map;
            } catch (Exception ignore) {
                // parse ไม่ได้  ไป live ต่อ
            }
        }
        return getLiveRatesToThb();
    }

    public Map<String, BigDecimal> getLiveRatesToThb() {
        try {
            String url = "https://open.er-api.com/v6/latest/THB";
            ResponseEntity<String> res = http.getForEntity(url, String.class);
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new RuntimeException("HTTP error");
            }
            JsonNode root = om.readTree(res.getBody());
            if (!"success".equalsIgnoreCase(root.path("result").asText())) {
                throw new RuntimeException("FX API result not success");
            }
            JsonNode rates = root.get("rates");
            Map<String, BigDecimal> toThb = new HashMap<>();
            toThb.put("THB", BigDecimal.ONE);

            Iterator<String> it = rates.fieldNames();
            while (it.hasNext()) {
                String ccy = it.next().toUpperCase(Locale.ROOT);
                BigDecimal thbToCcy = rates.get(ccy).decimalValue(); // 1 THB = thbToCcy CCY
                if (thbToCcy.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal ccyToThb = BigDecimal.ONE.divide(thbToCcy, MC); // 1 CCY = ? THB
                    toThb.put(ccy, ccyToThb);
                }
            }
            return toThb;
        } catch (Exception e) {
            return Map.of("THB", BigDecimal.ONE);
        }
    }


    public BigDecimal toThb(String currency, BigDecimal amount, Map<String, BigDecimal> ratesToThb) {
        if (amount == null) return null;
        String ccy = (currency == null || currency.isBlank()) ? "THB" : currency.toUpperCase(Locale.ROOT);
        BigDecimal rate = ratesToThb.getOrDefault(ccy, BigDecimal.ONE);
        BigDecimal thb = amount.multiply(rate, MC);
        return thb.setScale(OUT_SCALE, RoundingMode.HALF_UP);
    }
}
