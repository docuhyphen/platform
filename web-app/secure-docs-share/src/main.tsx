import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {FluentProvider} from "@fluentui/react-components";

import { createDarkTheme, createLightTheme } from '@fluentui/react-components';

import type { BrandVariants, Theme } from '@fluentui/react-components';

const maintheme: BrandVariants = {
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
    ...createLightTheme(maintheme),
};

const darkTheme: Theme = {
    ...createDarkTheme(maintheme),
};

darkTheme.colorBrandForeground1 = maintheme[110]; // use brand[110] instead of brand[100]
darkTheme.colorBrandForeground2 = maintheme[120]; // use brand[120] instead of brand[110]


createRoot(document.getElementById('root')!).render(
  <StrictMode>
      <FluentProvider theme={lightTheme}>
          <App/>
      </FluentProvider>
  </StrictMode>,
)
