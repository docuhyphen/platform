// @vitest-environment jsdom
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import EditorDialog from "./EditorDialog.tsx";

afterEach(cleanup);

describe("editor dialog dismissal", () => {
    it.each([true, false])("honours busy=%s when Escape requests dismissal", busy => {
        const dismiss = vi.fn();
        render(<EditorDialog id={"pending-editor"}
                             title={"Edit record"}
                             busy={busy}
                             onConfirm={vi.fn()}
                             onDismiss={dismiss}>
            <p id={"pending-editor-description"}>Record details</p>
        </EditorDialog>);

        fireEvent.keyDown(screen.getByRole("dialog", {name: "Edit record"}), {key: "Escape", code: "Escape"});

        expect(dismiss).toHaveBeenCalledTimes(busy ? 0 : 1);
    });
});
