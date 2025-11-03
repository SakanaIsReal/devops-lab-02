const { defineConfig } = require("cypress");

export default {
  e2e: {
    baseUrl: 'http://localhost:3000/', // URL ที่รัน frontend อยู่
    video: true,
    screenshotOnRunFailure: true,
    viewportWidth: 1440,
    viewportHeight: 900,
  },
}

