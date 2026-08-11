import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {WeatherMoonRegular, WeatherSunnyRegular} from "@fluentui/react-icons";
import type {ThemeMode} from "../../../context/theme.ts";

interface IndividualOnboardingThemeFieldProps
{
    radioLabelClassName: string;
    selectedTheme: ThemeMode;
    onThemeChange: (theme: ThemeMode) => void;
}

const IndividualOnboardingThemeField = ({
    radioLabelClassName,
    selectedTheme,
    onThemeChange,
}: IndividualOnboardingThemeFieldProps) => (
    <Field
        id={"individual-onboarding-theme-field"}
        label={"Choose your theme"}>
        <RadioGroup
            id={"individual-onboarding-theme-radio-group"}
            value={selectedTheme}
            onChange={(_, data) => onThemeChange(data.value as ThemeMode)}
            layout="horizontal"
            aria-label="Theme">
            <Radio
                id={"individual-onboarding-theme-light-radio"}
                value="light"
                label={
                    <span
                        id={"individual-onboarding-theme-light-label"}
                        className={radioLabelClassName}>
                        <WeatherSunnyRegular/> Light
                    </span>
                }
            />
            <Radio
                id={"individual-onboarding-theme-dark-radio"}
                value="dark"
                label={
                    <span
                        id={"individual-onboarding-theme-dark-label"}
                        className={radioLabelClassName}>
                        <WeatherMoonRegular/> Dark
                    </span>
                }
            />
        </RadioGroup>
    </Field>
);

export default IndividualOnboardingThemeField;
