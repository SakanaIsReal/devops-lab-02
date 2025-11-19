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
    cy.session(
        [email, password],
        () => {
            cy.visit(WebPageLocators.loginPage);
            cy.get(LoginLocators.usernameInput).type(email);
            cy.get(LoginLocators.passwordInput).type(password);
            cy.xpath(LoginLocators.submitButton).click();
            cy.url().should("include", WebPageLocators.homePage);
        },
        {
            cacheAcrossSpecs: true,
        }
    );
    cy.log(`✅ [ Actual ] : Sign-in Successfully`);

});
Cypress.Commands.add("signOut", () => {
    cy.visit(WebPageLocators.loginPage);
    cy.get(LoginLocators.usernameInput).type('man@example.com');
    cy.get(LoginLocators.passwordInput).type('123456');
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
    cy.wait(1000);
    cy.xpath(ProfileLocators.usernameInputBox).clear().type(username);
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
        cy.xpath('//button[text()="FINISH"]').click();
        cy.xpath('//*[@id="root"]/div/div/div[1]/h1').should(
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
            participants: ["NavadolSom", "SukiKana"],
        },
        {
            method: "Percentage",
            amount: "1500",
            itemname: "Alcohol",
            participants: ["NavadolSom", "SukiKana"],
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
                `.//summary[.//span[normalize-space(text())="Participants"]]`
            ).click();
            item.participants.forEach((p) => {
                cy.xpath(
                    `.//label[.//span[normalize-space(text())="${p}"]]//input[@type="checkbox"]`
                ).check({ force: true });
            });
            cy.xpath(
                `.//summary[.//span[contains(normalize-space(.),"Shared with")]]`
            ).click();
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

    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//p[contains(., "${expenseName}")]]//button[contains(., "Pay")]`
    )
        .should("be.visible")
        .click();
    cy.get('input[type="file"]').attachFile("Mockreciept.png");
    cy.xpath('//*[@id="root"]/div/div/div[2]/button').click();
    cy.visit(WebPageLocators.homePage);
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//p[contains(., "${expenseName}")]]//button[contains(., "Pay")]`
    )
        .should("be.visible")
        .click();
    cy.contains("Pending Payment Exists").should("be.visible");
});
Cypress.Commands.add("payExpenseFromGroup", (groupName, expenseName) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`,
        { timeout: 10000 }
    )
        .should("be.visible")
        .click();

    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible")
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")]//button[contains(., "Pay")]`
    )
        .should("be.visible")
        .click();
    cy.get('input[type="file"]').attachFile("Mockreciept.png");
    cy.xpath('//*[@id="root"]/div/div/div[2]/button').click();

    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(
        `//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`,
        { timeout: 10000 }
    )
        .should("be.visible")
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible")
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")]//button[contains(., "Pay")]`
    )
        .should("be.visible")
        .click();
    cy.contains("Pending Payment Exists").should("be.visible");
});

Cypress.Commands.add("conFirmExpensePay", (groupName, expenseName, participants) => {
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(`//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`, { timeout: 10000 })
        .should('be.visible')
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible")
        .click();
    for (const name of participants) {
        cy.log(`🔍 Verifying for ${name}`);

        cy.xpath(`//div[contains(@class,"flex items-center justify-between")][.//p[@class="font-semibold" and normalize-space(text())="${name}"]]//button[contains(., "Verify")]`, { timeout: 10000 })
            .should("be.visible")
            .click();
        cy.xpath('//button[normalize-space()="Accept"]', { timeout: 5000 })
            .should("be.visible")
            .click();
        cy.wait(500);
    }
    cy.visit(WebPageLocators.groupsPage);
    cy.xpath(`//h2[contains(normalize-space(.),"${groupName.trim()}")]//ancestor::a//button[contains(text(),"View")]`, { timeout: 10000 })
        .should('be.visible')
        .click();
    cy.xpath(
        `//div[contains(@class,"flex items-center justify-between")][.//h3[contains(.,"${expenseName.trim()}")]]//button[contains(., "Detail")]`
    )
        .should("be.visible")
        .click();

    for (const name of participants) {
        cy.log(`🔍 Verifying for ${name}`);
        cy.xpath(`//div[contains(@class,"flex items-center justify-between")][.//p[@class="font-semibold" and normalize-space(text())="${name}"]]//button[contains(., "Done")]`, { timeout: 10000 })
            .should("be.visible")
            .click();
    }
});
