import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {AddIcon, DeleteIcon} from "../../../components/IconBundles.tsx";
import {
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestTemplateEvidencePolicyRequest,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {attributeRequirementLabels, evidenceAttributeLabels} from "../templateAuthoringLabels.ts";
import {defaultEvidencePolicy} from "../templateDraftDocument.ts";
import {RequirementFieldsProps} from "../template-requirement-dialog/requirementDraft.ts";
import {useRequirementEvidenceAttributesStyles} from "./RequirementEvidenceAttributesStyles.tsx";

type AttributeRequirementKey =
    | "issuerRequirement"
    | "jurisdictionRequirement"
    | "languageRequirement"
    | "issueDateRequirement"
    | "expiryDateRequirement"
    | "coveragePeriodRequirement"
    | "certificationRequirement"
    | "signatureRequirement";

const CAPTURED_ATTRIBUTES: [AttributeRequirementKey, string][] = [
    ["issuerRequirement", "Issuer"],
    ["jurisdictionRequirement", "Jurisdiction"],
    ["languageRequirement", "Language"],
    ["issueDateRequirement", "Issue date"],
    ["expiryDateRequirement", "Expiry date"],
    ["coveragePeriodRequirement", "Coverage period"],
    ["certificationRequirement", "Certification reference"],
    ["signatureRequirement", "Signature reference"],
];

const RequirementEvidenceAttributes = ({id, draft, readOnly, onChange}: RequirementFieldsProps) =>
{
    const styles = useRequirementEvidenceAttributesStyles();
    const policy = draft.evidencePolicy ?? defaultEvidencePolicy();
    const [attribute, setAttribute] = useState(InformationRequestEvidenceAttribute.CONTENT_TYPE);
    const [acceptedValue, setAcceptedValue] = useState("");
    const accepted = policy.acceptedValues ?? [];
    const set = (change: Partial<InformationRequestTemplateEvidencePolicyRequest>) =>
        onChange({evidencePolicy: {...policy, ...change}});

    const addAccepted = () =>
    {
        set({acceptedValues: [...accepted, {attribute, acceptedValue: acceptedValue.trim()}]});
        setAcceptedValue("");
    };

    return (
        <div id={`${id}-evidence-attributes`}
             className={styles.section}>
            <Text id={`${id}-attributes-heading`}
                  weight={"semibold"}>
                What each file states
            </Text>
            <div id={`${id}-attribute-grid`}
                 className={styles.grid}>
                {CAPTURED_ATTRIBUTES.map(([key, label]) => (
                    <ChoiceSelect key={key}
                                  id={`${id}-${key}-select`}
                                  label={label}
                                  value={policy[key] ?? InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED}
                                  options={optionsFrom(attributeRequirementLabels)}
                                  disabled={readOnly}
                                  onChange={value => set({[key]: value})}/>
                ))}
            </div>
            <Text id={`${id}-accepted-heading`}
                  weight={"semibold"}>
                Accepted values
            </Text>
            {accepted.length === 0 && (
                <Text id={`${id}-accepted-empty`}
                      className={styles.muted}>
                    Any stated value is accepted.
                </Text>
            )}
            <ul id={`${id}-accepted-list`}
                className={styles.list}>
                {accepted.map((value, index) => (
                    <li key={`${value.attribute}-${value.acceptedValue}-${index}`}
                        id={`${id}-accepted-${index}`}
                        className={styles.item}>
                        <Text>{`${evidenceAttributeLabels[value.attribute]}: ${value.acceptedValue}`}</Text>
                        {!readOnly && (
                            <Button id={`${id}-accepted-${index}-remove`}
                                    appearance={"subtle"}
                                    shape={"circular"}
                                    icon={<DeleteIcon/>}
                                    aria-label={`Remove accepted value ${value.acceptedValue}`}
                                    onClick={() => set({acceptedValues: accepted.filter((_, position) => position !== index)})}/>
                        )}
                    </li>
                ))}
            </ul>
            {!readOnly && (
                <div id={`${id}-accepted-new`}
                     className={styles.newValue}>
                    <ChoiceSelect<InformationRequestEvidenceAttribute> id={`${id}-accepted-attribute-select`}
                                  label={"Attribute"}
                                  value={attribute}
                                  options={optionsFrom(evidenceAttributeLabels)}
                                  onChange={setAttribute}/>
                    <TextField id={`${id}-accepted-value-input`}
                               label={"Accepted value"}
                               value={acceptedValue}
                               onChange={setAcceptedValue}/>
                    <Button id={`${id}-accepted-add`}
                            appearance={"secondary"}
                            shape={"circular"}
                            icon={<AddIcon/>}
                            disabled={!acceptedValue.trim()}
                            onClick={addAccepted}>
                        Add value
                    </Button>
                </div>
            )}
        </div>
    );
};

export default RequirementEvidenceAttributes;
