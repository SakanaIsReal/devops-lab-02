describe("Test E2E Smart Split", () => {
  beforeEach(() => {
    cy.visit("http://localhost:3001");
  });
  // it("Sign in with valid account",()=>{
  //   cy.get('[data-cy="login-email-input"]').type("juti@gmail.com");
  //   cy.get('[data-cy="login-password-input"]').type("212");
  //   cy.get('[data-cy="login-submit-button"]').click();
  //      cy.wait(2000)
  // })
  // it("Create a new smart split group",()=>{
  //   cy.get('[data-cy="login-email-input"]').type("juti@gmail.com");
  //   cy.get('[data-cy="login-password-input"]').type("212");
  //   cy.get('[data-cy="login-submit-button"]').click();
  //   cy.wait(2000)
  //   cy.get('[data-cy="bottom-nav-groups"]').click();
  //   cy.get('[data-cy="fab-add-group"]').click();
  //   cy.get('[data-cy="input-group-name"]').type("Test Create Smart Split Group");
  //   cy.wait(2000)
  //   cy.get('[data-cy="input-participant"]').type("N")
  //   cy.wait(2000)
  //   cy.contains('li', 'N').click();
  //   cy.get('[data-cy="btn-create-group"]').click();
  //      cy.wait(2000)
  //  });
  it("Create new expense detail with equal split",()=>{
    cy.get('[data-cy="login-email-input"]').type("juti@gmail.com");
    cy.get('[data-cy="login-password-input"]').type("212");
    cy.get('[data-cy="login-submit-button"]').click();
    cy.wait(2000)
        cy.get('[data-cy="bottom-nav-groups"]').click();
        cy.get('[data-cy="btn-view-group"]').click();
        cy.get('[data-cy="fab-add-group"').click();
    })
});