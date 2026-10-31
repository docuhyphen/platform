import {Text} from "@fluentui/react-components";
import {InformationRequestSubjectDto, InformationRequestSubjectKind} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import {subjectKindLabels} from "../../shared/informationRequestLabels.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {AddPartyForm} from "../add-party-dialog/addPartyForm.ts";

interface Props
{
    form: AddPartyForm;
    subjects: InformationRequestSubjectDto[];
    referencesAllowed: boolean;
    disabled: boolean;
    onChange: (change: Partial<AddPartyForm>) => void;
}

const subjectLabel = (subject: InformationRequestSubjectDto): string =>
{
    const reference = subject.references[0];
    const kind = subjectKindLabels[subject.subjectKind];
    return reference ? `${kind}: ${reference.identifierType} ${reference.identifierValue}` : `${kind}: no reference`;
};

const SubjectPartyFields = ({form, subjects, referencesAllowed, disabled, onChange}: Props) => (
    <>
        {subjects.length > 0 && (
            <ChoiceSelect id={"information-request-add-party-known-subject"}
                          label={"Known subject"}
                          value={form.subjectId}
                          disabled={disabled}
                          options={[{value: "", label: "A new subject"}, ...subjects.map(subject => ({
                              value: subject.id,
                              label: subjectLabel(subject)
                          }))]}
                          onChange={subjectId => onChange({subjectId})}/>
        )}
        {!form.subjectId && (
            <>
                <ChoiceSelect id={"information-request-add-party-subject-kind"}
                              label={"Subject kind"}
                              value={form.subjectKind}
                              disabled={disabled}
                              options={optionsFrom<InformationRequestSubjectKind>(subjectKindLabels)}
                              onChange={subjectKind => onChange({subjectKind})}/>
                {!referencesAllowed && (
                    <Text id={"information-request-add-party-reference-unavailable"}
                          size={200}>
                        Naming a subject by a reference needs privacy management, such as an Organization Owner or
                        Administrator.
                    </Text>
                )}
            </>
        )}
        {!form.subjectId && referencesAllowed && (
            <>
                <TextField id={"information-request-add-party-authority"}
                           label={"Authority"}
                           hint={"Who issued the reference, for example the office that keeps the record."}
                           value={form.authority}
                           disabled={disabled}
                           maxLength={120}
                           onChange={authority => onChange({authority})}/>
                <TextField id={"information-request-add-party-identifier-type"}
                           label={"Identifier type"}
                           value={form.identifierType}
                           disabled={disabled}
                           maxLength={80}
                           onChange={identifierType => onChange({identifierType})}/>
                <TextField id={"information-request-add-party-identifier-value"}
                           label={"Identifier value"}
                           value={form.identifierValue}
                           disabled={disabled}
                           maxLength={200}
                           onChange={identifierValue => onChange({identifierValue})}/>
            </>
        )}
    </>
);

export default SubjectPartyFields;
