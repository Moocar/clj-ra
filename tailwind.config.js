module.exports = {
  purge: {
    content: [
      './src/**/*.cljs'
    ],
    extract: {
      md: (content) => {
	return /[\.^<>"'`\s]*[\.^<>"'`\s:]/g.match(content)
      }
    }
  },
  darkMode: false, // or 'media' or 'class'
  theme: {
    extend: {},
  },
  variants: {
    extend: {},
  },
  plugins: [],
}
