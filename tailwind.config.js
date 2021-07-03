module.exports = {
  purge: {
    content: [
      './src/**/*.cljs',
      './src/ra/server.clj'
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
