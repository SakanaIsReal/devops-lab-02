const { defineConfig } = require("cypress");

module.exports = defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:3000/', // URL where frontend is running
    video: true,
    screenshotOnRunFailure: true,
    viewportWidth: 1440,
    viewportHeight: 900,
    defaultCommandTimeout: 20000, // 10 seconds (default is 4 seconds)
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
  },
});

