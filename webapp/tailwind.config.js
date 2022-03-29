module.exports = {
  purge: ["../../../../src/main/resources/index.html", "../../../../src/**/*.scala"],
  plugins: [
    require('daisyui'),
  ],
  daisyui: {
    logs: false, // otherwise daisy logs its ui version
  },
};
