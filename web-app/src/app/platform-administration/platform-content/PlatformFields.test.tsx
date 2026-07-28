/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {FieldScopeKind} from "../../models/models.tsx";
import PlatformFields from "./PlatformFields.tsx";

const fieldDialogProps = vi.fn();
const schemaDialogProps = vi.fn();
const setFieldEditorOpen = vi.fn();
const setSchemaEditor = vi.fn();

vi.mock("./usePlatformFieldManagement.ts", () => ({
    usePlatformFieldManagement: () => ({
        fields: [],
        schemas: [],
        loading: false,
        error: null,
        fieldEditorOpen: false,
        schemaEditor: null,
        setFieldEditorOpen,
        setSchemaEditor,
        saved: vi.fn(),
        openSchema: vi.fn(),
        openSchemaVersion: vi.fn(),
        retireField: vi.fn(),
        publishSchema: vi.fn(),
        retireSchema: vi.fn(),
    }),
}));

vi.mock("../../settings/fields-tab/FieldDefinitionDialog.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        fieldDialogProps(props);
        return null;
    },
}));

vi.mock("../../settings/fields-tab/SchemaEditorDialog.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        schemaDialogProps(props);
        return null;
    },
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformFields", () =>
{
    it("enforces PLATFORM scope for field and schema editors", () =>
    {
        render(<PlatformFields/>);

        expect(fieldDialogProps).toHaveBeenCalledWith(expect.objectContaining({
            enforcedScope: FieldScopeKind.PLATFORM,
        }));
        expect(schemaDialogProps).toHaveBeenCalledWith(expect.objectContaining({
            enforcedScope: FieldScopeKind.PLATFORM,
            definitions: [],
        }));
        expect(screen.queryByText(
            "Manage PLATFORM-scoped fields and schemas without loading organization configuration.",
        )).toBeNull();
        expect(document.querySelector("#platform-field-configuration-create svg")).toBeTruthy();
        expect(document.querySelector(
            "#platform-fields-scrollable-content #platform-field-configuration-create",
        )).toBeNull();

        fireEvent.click(screen.getByText("Create platform field"));
        expect(setFieldEditorOpen).toHaveBeenCalledWith(true);

        fireEvent.click(document.querySelector("#platform-fields-schemas-tab") as HTMLElement);
        fireEvent.click(screen.getByText("Create platform schema"));
        expect(setSchemaEditor).toHaveBeenCalledWith("new");
    });
});
