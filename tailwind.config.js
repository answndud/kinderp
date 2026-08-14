/** @type {import('tailwindcss').Config} */
export default {
    content: [
        './src/main/resources/templates/**/*.html',
        './src/main/resources/static/js/**/*.js'
    ],
    theme: {
        extend: {
            colors: {
                primary: {
                    50: '#f2f8f4',
                    100: '#deeee3',
                    200: '#c1dfca',
                    300: '#96c7a6',
                    400: '#68ab81',
                    500: '#438f63',
                    600: '#347851',
                    700: '#2b6043',
                    800: '#244d37',
                    900: '#1e402f'
                }
            },
            fontFamily: {
                sans: ['Pretendard', 'Noto Sans KR', 'Apple SD Gothic Neo', 'sans-serif']
            }
        }
    },
    plugins: []
};
