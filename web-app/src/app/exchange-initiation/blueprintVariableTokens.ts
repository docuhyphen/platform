import {AvailableVariablesDto, BlueprintConfig, BlueprintDefinitionSummaryDto} from "../models/models.tsx";

export interface PendingVariableTokens
{
    tokens: string[];
    defaults: Record<string, string>;
}

const VARIABLE_TOKEN_PATTERN = /\{\{([^}]+)}}/g;

export const collectPendingVariableTokens = (
    availableVariables: AvailableVariablesDto,
    blueprint: BlueprintDefinitionSummaryDto,
    config: BlueprintConfig | null,
): PendingVariableTokens | null =>
{
    const systemTokenSet = new Set(availableVariables.system.map(s => s.token));
    const seqTokenSet = new Set(availableVariables.sequences.map(s => `SEQ:${s.key}`));
    const allStrings = [
        config?.name ?? '',
        config?.description ?? '',
        config?.initialShareMessage ?? '',
        ...(blueprint.exchangeDocuments ?? []).map(d => d.title),
    ];
    const found = new Set<string>();
    for (const s of allStrings)
    {
        for (const m of s.matchAll(VARIABLE_TOKEN_PATTERN))
        {
            const t = m[1].trim();
            if (!systemTokenSet.has(t) && !seqTokenSet.has(t)) found.add(t);
        }
    }
    const tokens = Array.from(found);
    if (tokens.length === 0) return null;

    const defaults: Record<string, string> = {};
    for (const t of tokens)
    {
        const orgVar = availableVariables.org.find(v => v.key === t);
        const persVar = availableVariables.personal.find(v => v.key === t);
        defaults[t] = orgVar?.defaultValue ?? persVar?.defaultValue ?? '';
    }
    return {tokens, defaults};
};
