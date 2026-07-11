import {describe, expect, it} from "vitest";
import {
    buildConditionExpression,
    getAllowedConditionOperators,
    parseConditionExpression,
} from "./conditionExpression.ts";
import {WorkflowSubjectFieldDto} from "../../../../models/models.tsx";

const field = (type: string, description?: string): WorkflowSubjectFieldDto => ({
    name: "value",
    type,
    description,
});

describe("condition expression wire contract", () =>
{
    it("orders longer operators without prefix collisions", () =>
    {
        expect(parseConditionExpression("$subject.value >= 5").operator).toBe(">=");
        expect(parseConditionExpression("$subject.value <= 5").operator).toBe("<=");
    });

    it("escapes and restores quoted values", () =>
    {
        const expression = buildConditionExpression("value", "==", "it's \\ safe", true);
        expect(expression).toBe("$subject.value == 'it\\'s \\\\ safe'");
        expect(parseConditionExpression(expression ?? "").value).toBe("it's \\ safe");
    });

    it("mirrors operators by field type", () =>
    {
        expect(getAllowedConditionOperators(field("STRING")).map(operator => operator.value))
            .toEqual(["==", "!=", "contains", "startsWith"]);
        expect(getAllowedConditionOperators(field("number")).map(operator => operator.value))
            .toEqual(["==", "!=", ">", "<", ">=", "<="]);
        expect(getAllowedConditionOperators(field("integer")).map(operator => operator.value))
            .toEqual(["==", "!=", ">", "<", ">=", "<="]);
        expect(getAllowedConditionOperators(field("boolean")).map(operator => operator.value))
            .toEqual(["==", "!="]);
        expect(getAllowedConditionOperators(field("UUID")).map(operator => operator.value))
            .toEqual(["==", "!="]);
        expect(getAllowedConditionOperators(field("STRING", "OPEN | CLOSED")).map(operator => operator.value))
            .toEqual(["==", "!="]);
    });
});
