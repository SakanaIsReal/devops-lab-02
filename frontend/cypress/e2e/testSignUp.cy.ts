describe('test signup', () => {
  it('should Sign up successfully and redirect to the dashboard', () => {
    cy.visit('http://localhost:3001')

    cy.get('[data-cy="login-signup-button"]').click()
    cy.get('[data-cy="username-input-signup"]').type('testsignup1')

    cy.get('[data-cy="email-input-signup"]').type('testsignup1@example.com')
    cy.get('[data-cy="phone-input-signup"]').type('+1234567890')
    cy.get('[data-cy="password-input-signup"]').type('TestPassword123!')
    cy.get('[data-cy="confirm-password-input-signup"]').type('TestPassword123!')
    cy.get('[data-cy="btn-signup"]').click()
  })
})