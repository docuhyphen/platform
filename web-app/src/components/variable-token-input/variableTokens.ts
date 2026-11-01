import {AvailableVariablesDto} from '../../app/models/models';

export type VariableTokenBadgeColor = 'brand' | 'success' | 'informative' | 'warning';

export interface VariableTokenMatch
{
    token: string;
    start: number;
    end: number;
}

const TOKEN_RE = /\{\{([^}]+)}}/g;

export function getTokens(value: string): VariableTokenMatch[]
{
    const tokens: VariableTokenMatch[] = [];
    let m: RegExpExecArray | null;
    TOKEN_RE.lastIndex = 0;
    while ((m = TOKEN_RE.exec(value)) !== null)
        tokens.push({token: m[1].trim(), start: m.index, end: m.index + m[0].length});
    return tokens;
}

export function getPreviewLabel(token: string, available: AvailableVariablesDto, resolved?: Record<string, string>): string
{
    if (resolved?.[token]) return resolved[token];
    const sys = available.system.find(s => s.token === token);
    if (sys) return sys.example;
    const seq = available.sequences.find(s => `SEQ:${s.key}` === token);
    if (seq) return seq.previewValue;
    const orgVar = available.org.find(v => v.key === token);
    if (orgVar) return orgVar.defaultValue ?? '';
    const personalVar = available.personal.find(v => v.key === token);
    if (personalVar) return personalVar.defaultValue ?? '';
    return `{{${token}}}`;
}

export function getTokenBadgeColor(token: string, available: AvailableVariablesDto): VariableTokenBadgeColor
{
    if (available.system.some(s => s.token === token)) return 'brand';
    if (token.startsWith('SEQ:')) return 'success';
    if (available.org.some(v => v.key === token)) return 'warning';
    return 'informative';
}
