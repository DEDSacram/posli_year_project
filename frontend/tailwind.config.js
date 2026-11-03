/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        cream: "#f9f8f3",
        creamdark: "#f6f4eb",
        navy: "#1a4480",
        navydeep: "#132644",
        brand: "#a82222",
        branddark: "#8b1c1c",
        btngray: "#b5b5b5",
        btngraydark: "#a3a3a3",
        footgray: "#e5e7eb",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};