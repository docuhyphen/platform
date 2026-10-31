import {SchemaDefinitionDto, SchemaFieldBindingDto, SchemaVersionDto} from "../../models/models.tsx";
import {getSchemaVersion} from "../../../services/fieldsService.ts";
import {useServerRead} from "../shared/useServerRead.ts";
import {listedSchemaVersion} from "./TemplateDocumentContext.ts";

export interface NamedSchemaVersion {
    bindings: SchemaFieldBindingDto[];
    known: boolean;
    error: string | null;
    reload: () => void;
}

const NO_VERSION_READ = "The Fields of this Template's Schema Version could not be loaded.";
const NO_BINDINGS: SchemaFieldBindingDto[] = [];

export const useNamedSchemaVersion = (schemas: SchemaDefinitionDto[], schemaVersionId?: string): NamedSchemaVersion => {
    const listed = listedSchemaVersion(schemas, schemaVersionId);
    const read = useServerRead<SchemaVersionDto | null>(
        `schema-version:${schemaVersionId ?? "none"}:${listed ? "listed" : "named"}`,
        () => listed || !schemaVersionId ? Promise.resolve(listed ?? null) : getSchemaVersion(schemaVersionId),
        () => NO_VERSION_READ,
    );
    const version = listed ?? read.value;
    return {
        bindings: version?.bindings ?? NO_BINDINGS,
        known: Boolean(version),
        error: listed ? null : read.error,
        reload: () => void read.reload(),
    };
};
