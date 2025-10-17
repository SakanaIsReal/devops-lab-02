describe('Homepage', () => {
  beforeEach(() => {
    cy.visit('/')
  })

  it('should load the homepage', () => {
    cy.get('h1').should('exist')
  })

  it('should have navigation links', () => {
    cy.get('nav').should('exist')
  })
})