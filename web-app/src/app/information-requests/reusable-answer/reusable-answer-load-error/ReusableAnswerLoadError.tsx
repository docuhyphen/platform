import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useReusableAnswerSettings} from "../ReusableAnswerContext.ts";

const ReusableAnswerLoadError = () => {
    const settings = useReusableAnswerSettings();
    if (!settings?.error) return null;

    return (
        <InformationRequestLoadError id={"information-request-reusable-answers-load-error"}
                                     message={settings.error}
                                     onRetry={() => void settings.reload()}/>
    );
};

export default ReusableAnswerLoadError;
