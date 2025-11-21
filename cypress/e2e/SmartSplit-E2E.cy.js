describe('Sign In , Sign Up and Sign Out', () => {
  it('TC 1 : Sign up with new account', () => {
    cy.signUp('MxnlodySoTest', 'TestCypress@example.com', '1234567890', '123456', '123456');
  });
  it('TC 2 : Sign up with existing account', () => {
    cy.existAccount_signUp('MxnlodySoTest', 'TestCypress@example.com', '1234567890', '123456', '123456');
  });
  it('TC 3 : Sign in with valid account', () => {
    cy.signIn('nicetest@gmail.com', 'gg');
  });
  it('TC 4 : Sign in with invalid account', () => {
    cy.invalid_signIn('abc@example.com', '123456');
  });
  it('TC 5 : Sign out', () => {
    cy.signOut();
  })
});

describe('Profile Editing', () => {
  beforeEach(() => {
    cy.signIn('nicetest@gmail.com', 'gg');
  });
  it('TC 6 : Edit profile', () => {
    cy.editProfile('Man-DevOps', '9876543210');
  });
});

describe('Group Management (CRUD)', () => {
  beforeEach(() => {
    cy.signIn('testcy@gmail.com', 'gg');
  });
  it('TC 7 : Create new group', () => {
    cy.createNewGroup('Cypress Add new Group', ['testest', 'MxnlodySoTest']);
    cy.createNewGroup('For delete Testing', ['testest']);
  });
  it('TC 8 : Read group', () => {
    cy.readGroup('Cypress Add new Group');
  });
  it('TC 9 : Update group', () => {
    cy.editGroup('Cypress Add new Group', 'We are BanYai');
  })
  it('TC 10 : Delete group', () => {
    cy.deleteGroup('For delete Testing');
  })
});

describe('Smart Split Functionality (Expense)', () => {
  beforeEach(() => {
    cy.signIn('testcy@gmail.com', 'gg');
  })
  it('TC 11 : Add expense in group with Equality split', () => {
    cy.addExpenseEqualSplit('We are BanYai', 'About us 10/10/2025', '1500');
  })
  it('TC 12 : Add expense in group with Manual split', () => {
    cy.addExpenseManualSplit("We are BanYai", "5 Star Restuarant")
  })
});



describe('Smart Split Functionality (Payment)', () => {
  beforeEach(() => {
    cy.visit('/login');
    cy.signIn('TestCypress@example.com', '123456');
  })
   it('TC 13 : Expense Payment from Dashboard ', () => {
    cy.payExpenseFromDashboard("5 Star Restuarant")
  })
  it('TC 14 : Expense Payment from Group', () => {
    cy.payExpenseFromGroup("We are BanYai ", "About us 10/10/2025")
  })
});

describe('Smart Split Functionality (Verify Payment)', () => {
  beforeEach(() => {
    cy.signIn('testcy@gmail.com', 'gg');
  })
  it('TC 15 : Confirm Payment', () => {
    cy.conFirmExpensePay("We are BanYai", "5 Star Restuarant", ["MxnlodySoTest"]);
  })
})

describe('Add user from another group', () => {
   beforeEach(() => {
     cy.signIn('testcy@gmail.com', 'gg');
   })
     it('TC 16 : Create new group', () => {
     cy.createNewGroupAnotheruser('Cypress Add new User from anotherGr', ['Man-DevOps'],'We are BanYai');
   });
})

describe('Check name invalid input', () => {
   beforeEach(() => {
     cy.signIn('testcy@gmail.com', 'gg');
   })
     it('TC 16 : InvalidInput', () => {
     cy.InvalidInput('CheckInvalidInput', 'Mxn-DevOps');
   });
})

describe('Check Bill TOtal', () => {
   beforeEach(() => {
     cy.signIn('testcy@gmail.com', 'gg');
   })
     it('TC 17 : Check Bill TOtal', () => {
     cy.checkTotalbill('We are BanYai', 'About us 10/10/2025');
   });
})

describe('Add expense USD currency & Set ExchageRate (Payment)', () => {
  beforeEach(() => {
    cy.signIn('TestCypress@example.com', '123456');
  })
   it('TC 18 : Add expense USD currency', () => {
     cy.addcurrencyUSD('We are BanYai', 'About us 10/10/2025', '26');
   })
   it('TC 19 : Set ExchageRate', () => {
     cy.setExchageRate('We are BanYai', 'About us 10/10/2025', '26','30');
   })
});

describe('Add expense Another currency (Payment)', () => {
  beforeEach(() => {
    cy.signIn('TestCypress@example.com', '123456');
  })
   it('TC 20 : Add expense Another currency', () => {
     cy.addAnothercurrency('We are BanYai', 'About us 10/10/2025','10', 'EUR');
   })
});