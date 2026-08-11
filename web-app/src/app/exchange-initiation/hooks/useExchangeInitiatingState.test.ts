// @vitest-environment jsdom

import {renderHook} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import useExchangeInitiatingState from "./useExchangeInitiatingState.ts";

describe("useExchangeInitiatingState", () =>
{
    it("initializes internal participants as an empty collection", () =>
    {
        const {result} = renderHook(() => useExchangeInitiatingState());

        expect(result.current.internalParticipants).toEqual([]);
    });
});
