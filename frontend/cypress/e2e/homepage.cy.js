describe('Homepage', () => {
  beforeEach(() => {
    // Mock a successful login before visiting the homepage
    cy.window().then((win) => {
      win.localStorage.setItem('token', 'mock-token');
    });
    cy.visit('/');
  });

  it('should load the homepage with navigation', () => {
    // Check for the Navbar component
    cy.get('[class*="navbar"]').should('exist');
    
    // Check for the Balance Summary component
    cy.get('[class*="balance-summary"]').should('exist');
    
    // Check for the Bottom Navigation
    cy.get('[class*="bottom-nav"]').should('exist');
  });

  it('should have balance list', () => {
    cy.get('[class*="balance-list"]').should('exist');
  });
});