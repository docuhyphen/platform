import {createRoot} from 'react-dom/client'
import '@fontsource-variable/manrope/index.css'
import './index.css'
import App from './App.tsx'
import {FluentProvider, SSRProvider} from "@fluentui/react-components";
import {createDOMRenderer, RendererProvider} from "@griffel/react";
import {BrowserRouter} from "react-router-dom";
import {lightTheme} from "./theme.ts";

const container = document.getElementById('docu-hyphen-app')!;
const renderer = createDOMRenderer(document);
const application = (
    <RendererProvider
        renderer={renderer}
        targetDocument={document}
    >
        <SSRProvider>
            <FluentProvider theme={lightTheme} id="fluent-provider">
                <BrowserRouter>
                    <App/>
                </BrowserRouter>
            </FluentProvider>
        </SSRProvider>
    </RendererProvider>
);

if (container.hasChildNodes())
{
    container.querySelectorAll<HTMLStyleElement>("#fluent-provider > style").forEach((styleElement) =>
    {
        document.head.appendChild(styleElement);
    });
    container.replaceChildren();
}
createRoot(container).render(application);
