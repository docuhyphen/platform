import FieldValueEditor from "../../../exchanges/components/exchange-fields-tab/FieldValueEditor.tsx";
import {InformationRequestResponseDto, SchemaFieldBindingDto} from "../../../models/models.tsx";
import ReusableAnswerSlot from "../../reusable-answer/reusable-answer-slot/ReusableAnswerSlot.tsx";
import {ResponseETagResult} from "../StructuredResponseWorkspaceTypes.ts";

interface Props {
    requestId: string;
    elementId: string;
    response: InformationRequestResponseDto;
    binding: SchemaFieldBindingDto;
    value: unknown;
    locked: boolean;
    onChange: (value: unknown) => void;
    onApplied: () => void;
    responseETag: string;
    busy: boolean;
    onResult: (result: ResponseETagResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const StructuredFieldValueInput = ({elementId, response, binding, value, locked, onChange, ...commands}: Props) => (
    <>
        {!locked && (
            <ReusableAnswerSlot elementId={elementId}
                                response={response}
                                shownValue={value}
                                options={binding.options}
                                {...commands}/>
        )}
        <FieldValueEditor id={`information-request-response-field-${elementId}`}
                          binding={binding}
                          value={value}
                          locked={locked}
                          onChange={onChange}
                          showLabel={false}
                          labelledBy={`information-request-response-prompt-${elementId}`}/>
    </>
);

export default StructuredFieldValueInput;
