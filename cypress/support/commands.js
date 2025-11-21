require("cypress-xpath");
import "cypress-file-upload";
import { LoginLocators } from "../locators/loginLocator.js";
import { WebPageLocators } from "../locators/webPageLocator.js";
import { ProfileLocators } from "../locators/profileLocator.js";
import { GroupAddLocator } from "../locators/groupAddLocator.js";
import { SignUpLocators } from "../locators/signUpLocator.js";
import { ExpenseLocator } from "../locators/expenseLocator.js";
import { EquallySplitLocator } from "../locators/equalSplitLocator.js";
import { ManualSplitLocator } from "../locators/manualSplitLocator.js";

Cypress.Commands.add("signIn", (email, password) => {
    cy.log(`🔍 [ Start ]  Sign in with Valid Email `);
    cy.log(`⚠️  [ Expected ] : Sign-in Successfully`);

    // --- ส่วนการทำงานจริง (ถอดออกมาจาก cy.session) ---
    cy.visit(WebPageLocators.loginPage);
    cy.get(LoginLocators.usernameInput).type(email);
    cy.get(LoginLocators.passwordInput).type(password);
    cy.xpath(LoginLocators.submitButton).click();
    
    // รอให้ URL เปลี่ยนเพื่อยืนยันว่าเข้าได้จริง
    cy.url().should("include", WebPageLocators.homePage);
    // -----------------------------------------------

    cy.log(`✅ [ Actual ] : Sign-in Successfully`);
});
Cypress.Commands.add("signOut", () => {
    cy.visit(WebPageLocators.loginPage);
    cy.get(LoginLocators.usernameInput).type('nicetest@gmail.com');
    cy.get(LoginLocators.passwordInput).type('gg');
    cy.xpath(LoginLocators.submitButton).click();
    cy.wait(6000)
    cy.get('[data-cy="User-account"]').click();
    cy.get('[data-cy="Sign-out"]').click();
})
Cypress.Commands.add("invalid_signIn", (email, password) => {
    cy.log(`🔍 [ Start ] : Sign in with Invalid Email `);
    cy.log(`⚠️  [ Expected ] : Sign-in Not Successfully`);
    cy.visit(WebPageLocators.loginPage);
    cy.get(LoginLocators.usernameInput).type(email);
    cy.get(LoginLocators.passwordInput).type(password);
    cy.xpath(LoginLocators.submitButton).click();
    cy.on("window:alert", (text) => {
        expect(text).to.equal("Invalid email or password");
    });
    cy.visit(WebPageLocators.loginPage);
    cy.log(`✅ [ Actual ] : Sign-in Not Successfully`);
});
Cypress.Commands.add(
    "signUp",
    (username, email, phone, password, confirmePwd) => {
        cy.log(`🔍 [ Start ] : Sign up`);
        cy.log(`⚠️  [ Expected ]: Sign up Successfully`);
        cy.visit(WebPageLocators.signUpPage);
        cy.xpath(SignUpLocators.usernameInputBox).type(username);
        cy.xpath(SignUpLocators.emailInputBox).type(email);
        cy.xpath(SignUpLocators.phoneInputBox).type(phone);
        cy.xpath(SignUpLocators.passwordInputBox).type(password);
        cy.xpath(SignUpLocators.confirmPasswordInputBox).type(confirmePwd);
        cy.xpath(SignUpLocators.signUpButton).click();
        cy.url().should("include", WebPageLocators.homePage);
        cy.log(`✅ [ Actual ] : Sign up Successfully`);

    }
);
Cypress.Commands.add(
    "existAccount_signUp",
    (username, email, phone, password, confirmePwd) => {
        cy.log(`🔍 [ Start ] : Sign up`);
        cy.log(`⚠️  [ Expected ]: Sign up Not Successfully`);
        cy.visit(WebPageLocators.signUpPage);
        cy.xpath(SignUpLocators.usernameInputBox).type(username);
        cy.xpath(SignUpLocators.emailInputBox).type(email);
        cy.xpath(SignUpLocators.phoneInputBox).type(phone);
        cy.xpath(SignUpLocators.passwordInputBox).type(password);
        cy.xpath(SignUpLocators.confirmPasswordInputBox).type(confirmePwd);
        cy.xpath(SignUpLocators.signUpButton).click();
        cy.url().should("not.include", WebPageLocators.homePage);
        cy.log(`✅ [ Actual ] : Sign up Not Successfully`);

    }
);
Cypress.Commands.add("editProfile", (username, phone) => {
    cy.visit(WebPageLocators.accountPage);
    cy.xpath(ProfileLocators.usernameInputBox, { timeout: 10000 }).should('be.visible').clear().type(username);
    cy.wait(1000);
    cy.xpath(ProfileLocators.phoneInputBox).clear().type(phone);
    cy.xpath(ProfileLocators.saveChangesButton).should("exist").click();
    cy.wait(2000);
    cy.visit(WebPageLocators.accountPage);
    cy.xpath(ProfileLocators.usernameInputBox).should("contain.value", username);
    cy.xpath(ProfileLocators.phoneInputBox).should("contain.value", phone);
});

Cypress.Commands.add("createNewGroup", (groupName, paticipant) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(GroupAddLocator.groupAddButton).click();
    cy.xpath(GroupAddLocator.groupNameInputBox).type(groupName);
    cy.get('input[type="file"]').attachFile("banyai_dreamworld.jpg");
    cy.wait(1000);
    for (let i = 0; i < paticipant.length; i++) {
        cy.xpath(GroupAddLocator.participantInputBox).type(paticipant[i]);
        cy.wait(1000);
        cy.xpath(`//ul//li[normalize-space(text())="${paticipant[i]}"]`).click();
        cy.wait(1000);
    }
    cy.xpath(GroupAddLocator.createGroupButton).click();
    cy.xpath(GroupAddLocator.groupNameHeader).should("contain.text", groupName);
});

Cypress.Commands.add("readGroup", (groupName) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"View")]`
    ).click();
    cy.xpath(GroupAddLocator.groupNameHeader).should("contain.text", groupName);
});

Cypress.Commands.add("editGroup", (groupName, newgroupName) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"Edit")]`
    ).click();
    cy.xpath(GroupAddLocator.groupNameInputBox).clear().type(newgroupName);
    cy.xpath(GroupAddLocator.saveChangeButton).click();
});

Cypress.Commands.add("deleteGroup", (groupName) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"Delete")]`
    ).click();
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(`//h2[text()="${groupName}"]`).should("not.exist");
});

Cypress.Commands.add(
    "addExpenseEqualSplit",
    (groupName, expenseName, totalAmount, except) => {
        cy.visit(WebPageLocators.groupsPage);
        cy.xpath(
            `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"View")]`
        ).click();
        cy.xpath(ExpenseLocator.expenseAddButton).click();
        cy.xpath(ExpenseLocator.equalSplit).click();
        cy.xpath(EquallySplitLocator.ExpenseNameInputBox).type(expenseName);
        cy.xpath(EquallySplitLocator.TotalAmountInputBox).type(totalAmount);
        cy.xpath(EquallySplitLocator.ExpandPaticipantButton).click();
        cy.wait(1000);
        if (except > 0) {
            for (let i = 0; i < except.length; i++) {
                cy.xpath(
                    `//label[span[normalize-space(text())="${except[i]}"]]//input`
                ).click();
            }
        }
        // cy.xpath('//button[text()="FINISH"]').click();
        cy.xpath('//*[@id="root"]/div/div/div[1]/div/h1').should(
            "contain.text",
            "Bill Detail"
        );
    }
);

Cypress.Commands.add("addExpenseManualSplit", (groupName, expenseName) => {
    const items = [
        {
            method: "Equal",
            amount: "1500",
            itemname: "อาหาร",
            participants: ['testest', 'MxnlodySoTest'],
        },
        {
            method: "Percentage",
            amount: "1500",
            itemname: "Alcohol",
            participants: ['testest', 'MxnlodySoTest'],
            percent: [25, 25],
        },
    ];

    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"View")]`
    ).click();
    cy.xpath(ExpenseLocator.expenseAddButton).click();
    cy.xpath(ExpenseLocator.manualSplit).click();
    cy.xpath(ManualSplitLocator.ExpenseNameInputBox).type(expenseName);
    items.forEach((item, index) => {
        if (index > 0) {
            cy.contains("+ Add Item").click();
        }
        const block = `(//div[contains(@class,"mb-4") and contains(@class,"bg-white")])[${index + 1
            }]`;
        cy.xpath(block).within(() => {
            cy.xpath(`.//button[normalize-space(text())="${item.method}"]`).click();
            cy.xpath(`.//input[@placeholder="0.00"]`).clear().type(item.amount);
            cy.xpath(`.//input[@placeholder="Enter item name"]`)
                .clear()
                .type(item.itemname);
            cy.xpath(
                `/html/body/div/div/div/div[3]/div/div[4]/div/div/button`
            ).last().click();
            item.participants.forEach((p) => {
                cy.xpath(
                    `.//label[.//span[normalize-space(text())="${p}"]]//input[@type="checkbox"]`
                ).check({ force: true });
            });
            cy.xpath(
                `//*[@id="root"]/div/div/div[3]/div/div[4]/div/div/button/span[2]`
            ).last().click();
            if (item.method === "Percentage" && item.percent) {
                item.percent.forEach((value, i) => {
                    cy.xpath(`(.//input[@type="number"])[${i + 2}]`)
                        .invoke("val", value)
                        .trigger("input");
                });
            }
        });
    });
    cy.contains("FINISH").click();
    cy.wait(5000)
});
Cypress.Commands.add("payExpenseFromDashboard", (expenseName) => {
    cy.visit(WebPageLocators.homePage);
    cy.wait(10000)
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//p[contains(., "${expenseName}")]]//button[contains(., "Pay")]`
    )
        .should("be.visible").first()
        .click();
    cy.get('input[type="file"]').attachFile("Mockreciept.png");
    cy.xpath('//*[@id="root"]/div/div/div[2]/button').click();
    cy.visit(WebPageLocators.homePage);
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//p[contains(., "${expenseName}")]]//button[contains(., "Pay")]`
    )
        .should("be.visible").first()
        .click();
    cy.contains("Pending Payment Exists").should("be.visible");
});
Cypress.Commands.add("payExpenseFromGroup", (groupName, expenseName) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`,
        { timeout: 10000 }
    )
        .should("be.visible").first()
        .click();

    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible").first()
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")]//button[contains(., "Pay")]`,
        { timeout: 10000 }
    )
        .should("be.visible").first()
        .click();
    cy.wait(10000);
    cy.get('input[type="file"]').attachFile("Mockreciept.png");
    cy.xpath('//*[@id="root"]/div/div/div[2]/button').click();

    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`,
        { timeout: 10000 }
    )
        .should("be.visible").first()
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible").first()
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")]//button[contains(., "Pay")]`
    )
        .should("be.visible").first()
        .click();
    cy.contains("Pending Payment Exists").should("be.visible");
});

Cypress.Commands.add("conFirmExpensePay", (groupName, expenseName, participants) => {
    // 1. เข้าหน้า Group และกด View
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(`//h2[contains(normalize-space(.),"${groupName.trim()}")]/ancestor::a//button[contains(text(),"View")]`, { timeout: 10000 })
        .should('be.visible').first()
        .click();

    // 2. กด Detail ของ Expense นั้น
    cy.xpath(
        `//div[contains(@class,"justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible").first()
        .click();

    // 3. วนลูปกด Verify ให้แต่ละคน
    for (const name of participants) {
        const cleanName = name.trim();
        cy.log(`🔍 Verifying for ${cleanName}`);

        // --- 🔥 จุดที่แก้ไข: แก้ XPath ให้หาจาก Text ชื่อคน โดยไม่ยึดติดกับ Class มากเกินไป ---
        cy.xpath(
            `//div[contains(@class, "justify-between")][.//p[contains(text(), "${cleanName}")]]//button[contains(text(), "Verify")]`, 
            { timeout: 10000 }
        )
        .should("be.visible")
        .click();

        // กด Accept (ใน Modal หรือ Popup)
        cy.xpath('//button[normalize-space()="Accept"]', { timeout: 5000 })
            .should("be.visible")
            .click();
            
        cy.wait(500); // รอ Animation นิดหน่อย
    }

    // ---------------------------------------------------------
    // ส่วนด้านล่างนี้คือการกลับเข้ามาหน้าเดิมเพื่อกด Done (ตาม Logic เดิมของคุณ)
    // ถ้า Flow จริงๆ ไม่ต้อง Refresh หน้า ก็สามารถลบส่วนนี้ออกได้
    // ---------------------------------------------------------
    
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(`//h2[contains(normalize-space(.),"${groupName.trim()}")]/ancestor::a//button[contains(text(),"View")]`, { timeout: 10000 })
        .should('be.visible').first()
        .click();
    
    cy.xpath(
        `//div[contains(@class,"justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible").first()
        .click();

    for (const name of participants) {
        const cleanName = name.trim();
        cy.log(`🔍 Marking Done for ${cleanName}`);
        
        // ใช้ Logic เดียวกันกับ Verify คือหาจากกล่องที่มีชื่อคนนั้น
        cy.xpath(
            `//div[contains(@class, "justify-between")][.//p[contains(text(), "${cleanName}")]]//button[contains(text(), "Done")]`, 
            { timeout: 10000 }
        )
        .should("be.visible")
        .click();
    }
});


Cypress.Commands.add("createNewGroupAnotheruser", (groupName, paticipant,another) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(GroupAddLocator.groupAddButton).click();
    cy.xpath(GroupAddLocator.groupNameInputBox).type(groupName);
    cy.get('input[type="file"]').attachFile("banyai_dreamworld.jpg");
    cy.wait(1000);
    for (let i = 0; i < paticipant.length; i++) {
        cy.xpath(GroupAddLocator.participantInputBox).type(paticipant[i]);
        cy.wait(1000);
        cy.xpath(`//ul//li[normalize-space(text())="${paticipant[i]}"]`).click();
        cy.wait(1000);
    }
    cy.xpath(GroupAddLocator.praticipantAnotherInput).type(another);
    cy.xpath(GroupAddLocator.praticipantAnotherClick).click();
    cy.xpath(GroupAddLocator.createGroupButton).click();
    cy.xpath(GroupAddLocator.groupNameHeader).should("contain.text", groupName);
    cy.xpath('//*[@id="root"]/div/nav[2]/div/button[2]').click();
});

Cypress.Commands.add("InvalidInput", (groupName, paticipant) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(GroupAddLocator.groupAddButton).click();
    cy.xpath(GroupAddLocator.groupNameInputBox).type(groupName);
    cy.get('input[type="file"]').attachFile("banyai_dreamworld.jpg");
    cy.wait(1000);
    cy.xpath(GroupAddLocator.participantInputBox).type(paticipant);
    cy.wait(5000);
    cy.xpath(`//*[@id="root"]/div/div/div/div/div/div[3]/div/ul/li[1]`).click();
    cy.wait(5000);
});
 
Cypress.Commands.add("checkTotalbill", (groupName, expenseName) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`,
        { timeout: 10000 }
    )
        .should("be.visible").first()
        .click();

    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible").first()
        .click();
    cy.xpath('//*[@id="root"]/div/div/div[2]/div[2]/div[2]/div').should(
        "contain.text",
        "Transactions Left"
    )
});

Cypress.Commands.add(
    "addcurrencyUSD",
    (groupName, expenseName, totalAmount, except) => {
        cy.visit(WebPageLocators.groupsPage);
        cy.xpath(
            `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"View")]`
        ).click();
        cy.xpath(ExpenseLocator.expenseAddButton).click();
        cy.xpath(ExpenseLocator.equalSplit).click();
        cy.xpath(EquallySplitLocator.ExpenseNameInputBox).type(expenseName);
        cy.xpath(EquallySplitLocator.TotalAmountInputBox).type(totalAmount);
        cy.xpath('//*[@id="root"]/div/div/div[2]/div/button').click();
        cy.xpath('//*[@id="root"]/div/div/div[2]/div/div/label[2]').click();
        cy.xpath('//*[@id="root"]/div/div/div[4]/button/span[1]').click();
        cy.wait(5000);
        cy.xpath('//*[@id="root"]/div/div/button[2]').click();
    }
);

Cypress.Commands.add(
    "setExchageRate",
    (groupName, expenseName, totalAmount,rate) => {
        cy.visit(WebPageLocators.groupsPage);
        cy.xpath(
            `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"View")]`
        ).click();
        cy.xpath(ExpenseLocator.expenseAddButton).click();
        cy.xpath(ExpenseLocator.equalSplit).click();
        cy.xpath(EquallySplitLocator.ExpenseNameInputBox).type(expenseName);
        cy.xpath(EquallySplitLocator.TotalAmountInputBox).type(totalAmount);
        cy.xpath('//*[@id="root"]/div/div/div[2]/div/button').click();
        cy.xpath('//*[@id="root"]/div/div/div[2]/div/div/label[2]').click();
        cy.xpath('//*[@id="root"]/div/div/div[4]/button/span[1]').click();
        cy.xpath('//*[@id="root"]/div/div/div[3]/label/span').click();
        cy.xpath('//*[@id="root"]/div/div/div[4]/div[1]/input').type(rate);
        cy.wait(5000);
        cy.xpath('//*[@id="root"]/div/div/button[2]').click();
    }
);

Cypress.Commands.add(
    "addAnothercurrency",
    (groupName, expenseName, totalAmount, currency) => {
        cy.visit(WebPageLocators.groupsPage);
        cy.xpath(
            `//h2[text()="${groupName}"]/ancestor::a//button[contains(text(),"View")]`
        ).click();
        cy.xpath(ExpenseLocator.expenseAddButton).click();
        cy.xpath(ExpenseLocator.equalSplit).click();
        cy.xpath(EquallySplitLocator.ExpenseNameInputBox).type(expenseName);
        cy.xpath(EquallySplitLocator.TotalAmountInputBox).type(totalAmount);
        cy.xpath('//*[@id="root"]/div/div/div[2]/div/button').click();
        cy.xpath('//*[@id="root"]/div/div/div[2]/div/div/label[4]').click();
        cy.xpath('//*[@id="root"]/div/div/div[2]/input').type(currency);
        cy.xpath('//*[@id="root"]/div/div/div[4]/button/span[1]').click(); 
        cy.wait(5000);
        cy.xpath('//*[@id="root"]/div/div/button[2]').click();
    }
);