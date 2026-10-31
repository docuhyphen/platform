import {FieldDataClassification, SchemaFieldBindingDto} from "../../models/models.tsx";

export const collectedFieldNote = (binding: SchemaFieldBindingDto | undefined): string | undefined => {
    if (!binding) return undefined;
    if (binding.isReadOnly) return "Its Request Schema marks this Field read-only, so nobody could answer it.";
    if (binding.visibility === FieldDataClassification.PUBLIC) return undefined;
    return "This Field is not marked Public, so only people inside the owner of a request can answer it. "
        + "Someone from outside, such as an external contact, cannot be given the role that answers it.";
};
