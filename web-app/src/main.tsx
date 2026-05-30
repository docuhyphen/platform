import {createRoot} from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {FluentProvider} from "@fluentui/react-components";
import {ThemeProvider} from "./context/ThemeContext";
import {useTheme} from "./context/themeContextBase";

const ThemedApp = () =>
{
    const {theme} = useTheme();
    return (
        <FluentProvider theme={theme} id="fluent-provider">
            <App/>
        </FluentProvider>
    );
};

createRoot(document.getElementById('docu-hyphen-app')!).render(
    // <StrictMode>
    <ThemeProvider>
        <ThemedApp/>
    </ThemeProvider>
    // </StrictMode>,
)
