/** @vitest-environment jsdom */
import {cleanup, renderHook} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {LoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestOperationsAssigneeDto,
    InformationRequestOperationsPageDto,
    InformationRequestShareRoleKey,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../../models/models.tsx";
import {useLearnedAssignees} from "./useLearnedAssignees.ts";

const assignee: InformationRequestOperationsAssigneeDto = {
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    principalKind: "USER",
    principalId: "user-b",
    label: "Morgan Lee",
};

const answered: LoadedValue<InformationRequestOperationsPageDto> = {
    value: {
        items: [{
            requestId: "request-a-0000",
            exchangeId: "exchange-a",
            title: "Periodic records request",
            assignees: [assignee],
            templateVersionId: "version-a",
            state: InformationRequestState.IN_PROGRESS,
            gatesExchangeClosure: true,
            createdAt: Date.parse("2026-09-20T08:00:00Z"),
            ageSeconds: 3600,
            slaStatus: InformationRequestSlaStatus.ON_TRACK,
            clockCount: 0,
            reminderCount: 0,
            noticeCounts: {},
            exceptionCounts: {},
            executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        }],
        total: 1,
        limit: 25,
        offset: 0,
    },
    error: null,
    reload: () => undefined,
};

const pending: LoadedValue<InformationRequestOperationsPageDto> = {value: null, error: null, reload: () => undefined};

const refused: LoadedValue<InformationRequestOperationsPageDto> = {
    value: null,
    error: "Access denied to this owner's queue",
    reload: () => undefined,
};

const emptyAnswer: LoadedValue<InformationRequestOperationsPageDto> = {
    value: {items: [], total: 0, limit: 25, offset: 0},
    error: null,
    reload: () => undefined,
};

const renderLearned = () => {
    const renders: { page: LoadedValue<InformationRequestOperationsPageDto>; names: string[] }[] = [];
    const hook = renderHook(({page}) => {
        const learned = useLearnedAssignees(page);
        renders.push({page, names: learned.map(entry => entry.label ?? entry.principalId)});
        return learned;
    }, {initialProps: {page: answered}});
    return {hook, renders};
};

describe("useLearnedAssignees", () => {
    afterEach(cleanup);

    it("offers no learned name in any render of a refused read", () => {
        const {hook, renders} = renderLearned();
        expect(hook.result.current.map(entry => entry.label)).toEqual(["Morgan Lee"]);

        hook.rerender({page: refused});

        expect(renders.filter(entry => entry.page === refused).map(entry => entry.names)).not.toContainEqual(["Morgan Lee"]);
        expect(hook.result.current).toEqual([]);
    });

    it("withholds learned names while the next read after a refusal is pending and offers them once a read answers", () => {
        const {hook} = renderLearned();
        hook.rerender({page: refused});

        hook.rerender({page: pending});
        expect(hook.result.current).toEqual([]);

        hook.rerender({page: emptyAnswer});
        expect(hook.result.current.map(entry => entry.label)).toEqual(["Morgan Lee"]);
    });

    it("keeps learned names while a read without a refusal is pending", () => {
        const {hook} = renderLearned();

        hook.rerender({page: pending});

        expect(hook.result.current.map(entry => entry.label)).toEqual(["Morgan Lee"]);
    });
});
