import {
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestReviewVisibility,
} from "../../../models/models.tsx";
import {correctionScopeLabels, findingSeverityLabels, visibilityLabels} from "../reviewLabels.ts";
import ReviewFindingChoice from "./ReviewFindingChoice.tsx";

interface Props {
    severity: InformationRequestFindingSeverity;
    visibility: InformationRequestReviewVisibility;
    scope: InformationRequestFindingCorrectionScope;
    disabled: boolean;
    onSeverityChange: (severity: InformationRequestFindingSeverity) => void;
    onVisibilityChange: (visibility: InformationRequestReviewVisibility) => void;
    onScopeChange: (scope: InformationRequestFindingCorrectionScope) => void;
}

const ReviewFindingClassificationFields = (props: Props) => (
    <>
        <ReviewFindingChoice id={"information-request-review-finding-severity"}
                             label={"Severity"}
                             options={Object.values(InformationRequestFindingSeverity)}
                             labelOf={value => findingSeverityLabels[value]}
                             value={props.severity}
                             disabled={props.disabled}
                             onChange={props.onSeverityChange}/>
        <ReviewFindingChoice id={"information-request-review-finding-visibility"}
                             label={"Who can see it"}
                             options={Object.values(InformationRequestReviewVisibility)}
                             labelOf={value => visibilityLabels[value]}
                             value={props.visibility}
                             disabled={props.disabled}
                             onChange={props.onVisibilityChange}/>
        <ReviewFindingChoice id={"information-request-review-finding-scope"}
                             label={"What the respondent may change"}
                             options={Object.values(InformationRequestFindingCorrectionScope)}
                             labelOf={value => correctionScopeLabels[value]}
                             value={props.scope}
                             disabled={props.disabled}
                             onChange={props.onScopeChange}/>
    </>
);

export default ReviewFindingClassificationFields;
