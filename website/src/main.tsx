import {createRoot} from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import type {BrandVariants, Theme} from '@fluentui/react-components';
import {createDarkTheme, createLightTheme, FluentProvider} from "@fluentui/react-components";
import {BrowserRouter} from "react-router-dom";

const mainTheme: BrandVariants = {
    10: "#030204",
    20: "#17161E",
    30: "#232433",
    40: "#2D2F46",
    50: "#363C59",
    60: "#3E496C",
    70: "#455681",
    80: "#4B6496",
    90: "#5073AB",
    100: "#5482C1",
    110: "#5691D7",
    120: "#5EA1E7",
    130: "#77AFEB",
    140: "#8FBEEE",
    150: "#A7CDF2",
    160: "#BFDBF5"
};

const lightTheme: Theme = {
    ...createLightTheme(mainTheme),
};

const darkTheme: Theme = {
    ...createDarkTheme(mainTheme),
};

darkTheme.colorBrandForeground1 = mainTheme[110];
darkTheme.colorBrandForeground2 = mainTheme[120];

createRoot(document.getElementById('docu-hyphen-app')!).render(
    <FluentProvider theme={lightTheme} id="fluent-provider">
        <BrowserRouter>
            <App/>
        </BrowserRouter>
    </FluentProvider>
)
