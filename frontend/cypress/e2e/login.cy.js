describe('Login Page', () => {
  beforeEach(() => {
    cy.visit('/');
  });

  it('should show login form', () => {
    cy.get('form').should('exist');
  });
});