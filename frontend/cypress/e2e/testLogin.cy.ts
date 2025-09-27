describe('test success-login', () => {
  it('should log in successfully and redirect to the dashboard', () => {
    cy.visit('http://localhost:3001');

    cy.get('[data-cy="login-email-input"]').type('juti@gmail.com');

    cy.get('[data-cy="login-password-input"]').type('212');

    cy.get('[data-cy="login-submit-button"]').click();


  });
});
