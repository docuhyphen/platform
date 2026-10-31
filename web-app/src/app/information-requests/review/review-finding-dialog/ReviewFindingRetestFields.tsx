import {InformationRequestRetestResult, InformationRequestReviewFindingDto} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {retestResultLabels} from "../reviewLabels.ts";

export const NOT_A_RETEST = "none";

interface Props {
    earlierFindings: InformationRequestReviewFindingDto[];
    retestsFindingId: string;
    retestResult: InformationRequestRetestResult;
    disabled: boolean;
    onRetestChange: (retestsFindingId: string) => void;
    onResultChange: (retestResult: InformationRequestRetestResult) => void;
}

const ReviewFindingRetestFields = (props: Props) => {
    if (props.earlierFindings.length === 0) return null;
    return (
        <>
            <ChoiceSelect id={"information-request-review-finding-retest"}
                          label={"Retests an earlier finding"}
                          value={props.retestsFindingId}
                          disabled={props.disabled}
                          options={[
                              {value: NOT_A_RETEST, label: "Not a retest"},
                              ...props.earlierFindings.map(finding => ({
                                  value: finding.id,
                                  label: `${finding.reasonCode}: ${finding.narrative}`
                              })),
                          ]}
                          onChange={props.onRetestChange}/>
            {props.retestsFindingId !== NOT_A_RETEST && (
                <ChoiceSelect id={"information-request-review-finding-retest-result"}
                              label={"Retest result"}
                              value={props.retestResult}
                              disabled={props.disabled}
                              options={Object.values(InformationRequestRetestResult)
                                  .map(result => ({value: result, label: retestResultLabels[result]}))}
                              onChange={props.onResultChange}/>
            )}
        </>
    );
};

export default ReviewFindingRetestFields;
