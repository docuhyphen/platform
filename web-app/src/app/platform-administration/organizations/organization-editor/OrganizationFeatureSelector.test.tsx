/** @vitest-environment jsdom */
import {cleanup, fireEvent, render} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import OrganizationFeatureSelector from "./OrganizationFeatureSelector.tsx";
import {PlanFeature} from "../../../models/models.tsx";

describe("OrganizationFeatureSelector", () =>
{
    afterEach(cleanup);

    const openSelector = () =>
    {
        fireEvent.click(document.getElementById("platform-organization-feature-selector")!);
    };

    const option = (feature: PlanFeature) =>
        document.getElementById(`platform-organization-feature-option-${feature.toLowerCase()}`);

    it("offers every product feature so a held back one can still be granted", () =>
    {
        render(<OrganizationFeatureSelector selectedFeatures={[]}
                                            disabled={false}
                                            onChange={vi.fn()}/>);

        openSelector();

        Object.values(PlanFeature).forEach((feature) =>
        {
            expect(option(feature), `${feature} must be offered`).not.toBeNull();
        });
        expect(option(PlanFeature.INFORMATION_REQUESTS)?.textContent)
            .toContain("Information requests");
    });

    it("reports the feature the administrator selected", () =>
    {
        const onChange = vi.fn();
        render(<OrganizationFeatureSelector selectedFeatures={[]}
                                            disabled={false}
                                            onChange={onChange}/>);

        openSelector();
        fireEvent.click(option(PlanFeature.INFORMATION_REQUESTS)!);

        expect(onChange).toHaveBeenCalledWith([PlanFeature.INFORMATION_REQUESTS]);
    });
});
