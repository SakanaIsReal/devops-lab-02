describe('test success-login and then log out', () => {
  it('should log in successfully and redirect to the dashboard,and then it will', () => {
    cy.visit('http://localhost:3001');

    cy.get('[data-cy="login-email-input"]').type('juti@gmail.com');

    cy.get('[data-cy="login-password-input"]').type('212');

    cy.get('[data-cy="login-submit-button"]').click();
    cy.wait(2000)

    cy.get('[data-cy="user-menu-button"]').click();
    cy.wait(500);

    cy.get('[data-cy="logout-button"]').click();
  });
});