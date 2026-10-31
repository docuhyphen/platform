import {Field, Textarea} from "@fluentui/react-components";
import {InformationRequestResponseDisposition} from "../../../models/models.tsx";
import {narrativeFieldFor} from "../structured-response-requirement/structuredResponseRequirementState.ts";

interface Props {
    elementId: string;
    promptId: string;
    disposition: InformationRequestResponseDisposition;
    value: string;
    locked: boolean;
    onChange: (narrative: string) => void;
}

const NARRATIVE_LIMIT = 4000;

const ResponseNarrativeInput = ({elementId, promptId, disposition, value, locked, onChange}: Props) => {
    const field = narrativeFieldFor(disposition);
    if (!field) return null;
    const id = `information-request-response-narrative-${elementId}`;

    return (
        <Field id={`${id}-field`}
               label={{id: `${id}-label`, children: field.label}}
               hint={field.hint}
               required={field.required}>
            <Textarea id={id}
                      aria-labelledby={`${id}-label ${promptId}`}
                      value={value}
                      disabled={locked}
                      maxLength={NARRATIVE_LIMIT}
                      resize={"vertical"}
                      onChange={(_, data) => onChange(data.value)}/>
        </Field>
    );
};

export default ResponseNarrativeInput;
