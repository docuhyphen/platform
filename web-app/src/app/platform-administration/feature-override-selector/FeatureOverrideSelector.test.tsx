/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {PlanFeature} from "../../models/models.tsx";
import FeatureOverrideSelector from "./FeatureOverrideSelector.tsx";

describe("FeatureOverrideSelector", () => {
    afterEach(cleanup);

    const openSelector = (idPrefix: string) => {
        fireEvent.click(document.getElementById(`${idPrefix}-feature-selector`)!);
    };

    const option = (idPrefix: string, feature: PlanFeature) =>
        document.getElementById(`${idPrefix}-feature-option-${feature.toLowerCase()}`);

    it("offers every product feature so a held back one can still be granted", () => {
        render(<FeatureOverrideSelector idPrefix={"platform-organization"}
                                        ownerDescription={"this organization"}
                                        selectedFeatures={[]}
                                        disabled={false}
                                        onChange={vi.fn()}/>);

        openSelector("platform-organization");

        Object.values(PlanFeature).forEach((feature) => {
            expect(option("platform-organization", feature), `${feature} must be offered`).not.toBeNull();
        });
        expect(option("platform-organization", PlanFeature.INFORMATION_REQUESTS)?.textContent)
            .toContain("Information requests");
    });

    it("reports the feature the administrator selected", () => {
        const onChange = vi.fn();
        render(<FeatureOverrideSelector idPrefix={"platform-organization"}
                                        ownerDescription={"this organization"}
                                        selectedFeatures={[]}
                                        disabled={false}
                                        onChange={onChange}/>);

        openSelector("platform-organization");
        fireEvent.click(option("platform-organization", PlanFeature.INFORMATION_REQUESTS)!);

        expect(onChange).toHaveBeenCalledWith([PlanFeature.INFORMATION_REQUESTS]);
    });

    it("describes the owner it configures and offers only the features given to it", () => {
        render(<FeatureOverrideSelector idPrefix={"platform-user"}
                                        ownerDescription={"this account"}
                                        features={[PlanFeature.INFORMATION_REQUESTS, PlanFeature.WORKFLOW_AUTOMATION]}
                                        selectedFeatures={[PlanFeature.WORKFLOW_AUTOMATION]}
                                        disabled={false}
                                        onChange={vi.fn()}/>);

        expect(screen.getByText("Select features to configure for this account")).toBeTruthy();
        expect(screen.getByRole("combobox", {name: "Features"}).textContent).toContain("1 feature selected");
        openSelector("platform-user");
        expect(option("platform-user", PlanFeature.INFORMATION_REQUESTS)).not.toBeNull();
        expect(option("platform-user", PlanFeature.PERSONAL_REMINDERS)).toBeNull();
    });
});
