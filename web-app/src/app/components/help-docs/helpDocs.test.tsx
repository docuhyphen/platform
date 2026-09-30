/** @vitest-environment jsdom */
import {readdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {afterEach, describe, expect, it} from 'vitest';
import {cleanup, render} from '@testing-library/react';
import {getHelpDocArticleById, getHelpDocSections, HELP_DOC_ARTICLES} from './helpDocsRegistry';

const HELP_DOCS_DIR = join(process.cwd(), 'src', 'app', 'components', 'help-docs');
const SECTIONS_DIR = join(HELP_DOCS_DIR, 'sections');
const ARTICLES_DIR = join(SECTIONS_DIR, 'articles');

/** Lines in a file, counted the way a line limit means it: a trailing newline terminates the last line. */
const lineCount = (path: string): number =>
{
    const text = readFileSync(path, 'utf8');
    return text === '' ? 0 : text.replace(/\n$/, '').split('\n').length;
};

/** The prose one article renders, with runs of whitespace flattened so a line break never splits a phrase. */
const articleText = (articleId: string): string =>
{
    const article = getHelpDocArticleById(articleId);
    if (!article) throw new Error(`No help article ${articleId}`);
    const {container} = render(<>{article.content}</>);
    return (container.textContent ?? '').replace(/\s+/g, ' ');
};

afterEach(cleanup);

describe('help documentation registry', () =>
{
    it('answers with every section, in the order the composition lists them', () =>
    {
        const ids = getHelpDocSections().map(section => section.id);

        expect(ids).toEqual([
            'start-here', 'identity', 'exchanges', 'admin-operations', 'workflows',
            'blueprints', 'variables', 'fields', 'information-requests', 'communications', 'document-library',
        ]);
        expect(HELP_DOC_ARTICLES.map(article => article.sectionId)).toEqual(
            getHelpDocSections().flatMap(section => section.articles.map(() => section.id)),
        );
    });

    it('files every Information Request article in its own section and keeps Fields to Fields', () =>
    {
        const articlesOf = (sectionId: string) =>
            getHelpDocSections().find(section => section.id === sectionId)?.articles.map(article => article.id);

        expect(articlesOf('fields')).toEqual(['fields-overview', 'using-exchange-fields']);
        expect(articlesOf('information-requests')).toEqual([
            'information-requests-overview', 'request-templates', 'request-creating', 'request-access',
            'request-submission', 'request-evidence', 'request-review', 'request-outcomes', 'request-external-sources',
            'request-operations', 'record-preservation',
        ]);
    });

    it('names no individual section, so the next one is added without editing it', () =>
    {
        const source = readFileSync(join(HELP_DOCS_DIR, 'helpDocsRegistry.tsx'), 'utf8');

        expect(source).not.toMatch(/[.][/]sections[/]\w+Section["']/);
    });

    it('stays inside the size budget that keeps room for the next section', () =>
    {
        expect(lineCount(join(HELP_DOCS_DIR, 'helpDocsRegistry.tsx'))).toBeLessThan(60);
    });

    it('keeps every section and article inside its size budget', () =>
    {
        const oversizedSections = readdirSync(SECTIONS_DIR)
            .filter(name => name.endsWith('.tsx'))
            .filter(name => lineCount(join(SECTIONS_DIR, name)) >= 300);
        const oversizedArticles = readdirSync(ARTICLES_DIR)
            .filter(name => lineCount(join(ARTICLES_DIR, name)) >= 150);

        expect(oversizedSections).toEqual([]);
        expect(oversizedArticles).toEqual([]);
    });
});

describe('help documentation states what the platform actually does', () =>
{
    it('does not promise that a required document gates completing an Exchange', () =>
    {
        const text = articleText('managing-blueprints');

        expect(text).toMatch(/Required/);
        expect(text).not.toMatch(/must be uploaded before the Exchange can be completed/i);
        expect(text).toMatch(/(does not|is not a) (gate|block|prevent|stop)/i);
    });

    it('says a read-only field is filled only from a default the schema editor cannot set yet', () =>
    {
        const text = articleText('using-exchange-fields');

        expect(text).toMatch(/read.only/i);
        expect(text).not.toMatch(/A schema author can give a field a default value/i);
        expect(text).toMatch(/stays empty/i);
    });

    it('separates losing the Business Fields entitlement from a subscription that only refuses changes', () =>
    {
        const usingFields = articleText('using-exchange-fields');
        const overview = articleText('fields-overview');

        for (const text of [usingFields, overview])
        {
            expect(text).toMatch(/(hidden|not shown|no longer shown)/i);
            expect(text).toMatch(/(nothing is deleted|never removed|is not deleted|are kept)/i);
        }
        expect(usingFields).not.toMatch(/remain visible even when new assignments or edits are refused/i);
        expect(overview).not.toMatch(/existing definitions, schemas, assignments, and values stay readable/i);
    });

    it('says an unanswered field matches is empty rather than always skipping the workflow', () =>
    {
        const text = articleText('workflow-applicability');

        expect(text).not.toMatch(/skipped when .*a referenced field has no value/i);
        expect(text).toMatch(/is empty/i);
        expect(text).toMatch(/(unanswered|no value|left blank|blank)/i);
    });

    it('says a Template that routes work to a reviewer is issued and its submissions wait for review', () =>
    {
        const submission = articleText('request-submission');
        const review = articleText('request-review');

        expect(submission).not.toMatch(/refused at issuance until review is available/i);
        expect(submission).toMatch(/waits for review/i);
        expect(review).toMatch(/Nothing takes effect until you select Record decisions/);
        expect(review).toMatch(/only the items marked Changes required/i);
        expect(review).not.toMatch(/Exchange.s fields are updated/i);
    });

    it('lets owners and administrators see the whole queue while request details stay with the people who manage each request', () =>
    {
        const text = articleText('request-operations');

        expect(text).toMatch(/Organization Owners and Administrators/);
        expect(text).toMatch(/Exchange owner or a decision maker/i);
        expect(text).toMatch(/reconcil/i);
        expect(text).toMatch(/masked/i);
        expect(text).not.toMatch(/every member of the organization/i);
    });

    it('says a released hold keeps its history and that disposal keeps files other records still use', () =>
    {
        const text = articleText('record-preservation');

        expect(text).toMatch(/history stay on record/i);
        expect(text).toMatch(/another record still uses/i);
        expect(text).toMatch(/nothing about the subject is deleted/i);
    });

    it('limits Exchange Field conditions to Exchange triggers', () =>
    {
        expect(articleText('request-trigger-events')).toMatch(/Exchange Field conditions apply only to Exchange triggers/);
        expect(articleText('workflow-applicability')).toMatch(/apply only to Exchange triggers/);
    });

    it('explains ending an Exchange whose Information Requests are still open', () =>
    {
        const text = articleText('exchange-lifecycle');

        expect(text).toMatch(/Cancel requests and end/);
        expect(text).toMatch(/must finish first/i);
    });

    it('introduces Information Requests where they appear, for the plans that include them', () =>
    {
        const text = articleText('information-requests-overview');

        expect(text).toMatch(/Information Requests tab/);
        expect(text).toMatch(/Personal and Business plans/);
        expect(text).toMatch(/whatever their own plan/i);
        expect(text).toMatch(/next action/i);
        expect(text).not.toMatch(/hidden requests are counted/i);
    });

    it('describes the Template editor as it is and no longer the single-Field draft', () =>
    {
        const text = articleText('request-templates');

        expect(text).toMatch(/Sections, Groups, Conditions, Review, and Settings/);
        expect(text).toMatch(/Before publishing/);
        expect(text).not.toMatch(/not available in this deployment/i);
        expect(text).not.toMatch(/choose the Field that will collect the answer/i);
    });

    it('explains creating, issuing, and managing a request from its Exchange', () =>
    {
        const text = articleText('request-creating');

        expect(text).toMatch(/New Information Request/);
        expect(text).toMatch(/a Template, a Blueprint, or a one-off/i);
        expect(text).toMatch(/Make me the Decision Maker/);
        expect(text).toMatch(/shown once/i);
        expect(text).toMatch(/Resend link/);
        expect(text).toMatch(/Preview as recipient/);
    });

    it('keeps the access link and verification rules together', () =>
    {
        const text = articleText('request-access');

        expect(text).toMatch(/24 hours/);
        expect(text).toMatch(/three contact codes/i);
        expect(text).toMatch(/Free/);
    });

    it('describes saving answers the way the response workspace saves them', () =>
    {
        const text = articleText('request-submission');

        expect(text).toMatch(/two seconds/i);
        expect(text).toMatch(/Save responses/);
        expect(text).toMatch(/keeps your unsaved changes/i);
        expect(text).toMatch(/Review and submit/);
    });

    it('says an interrupted upload is kept for a retry', () =>
    {
        expect(articleText('request-evidence')).toMatch(/Retry/);
    });

    it('covers reviewer assignment, recusal, delegation, overrides, and reconsideration', () =>
    {
        const text = articleText('request-review');

        for (const phrase of ['Assign reviewer', 'Recuse', 'Delegate', 'Override', 'Reconsider', 'Reviewers only'])
        {
            expect(text).toContain(phrase);
        }
    });

    it('explains accepted facts, business decisions, and corrections in the management workspace', () =>
    {
        const text = articleText('request-outcomes');

        expect(text).toMatch(/Accepted facts/);
        expect(text).toMatch(/Business decisions/);
        expect(text).toMatch(/Corrections/);
        expect(text).toMatch(/confirms it is still accurate/i);
        expect(text).toContain('Supporting evidence to keep with the fact');
        expect(text).toMatch(/after it closes/);
        expect(articleText('request-submission')).toContain('Use this answer');
    });

    it('explains imported values, reconciliation, connectors, and generated outputs as records that never change an answer', () =>
    {
        const text = articleText('request-external-sources');

        for (const phrase of [
            '/information-requests/{id}/imported-values', '/imported-value-reconciliations',
            '/imported-value-discrepancies/{discrepancyId}/resolutions', '/connector-exchanges', '/generated-outputs',
            'MATCHES', 'DIFFERS', 'NOT_COMPARABLE', 'RESPONSE_STANDS', 'FOLLOW_UP_REQUESTED',
        ])
        {
            expect(text).toContain(phrase);
        }
        expect(text).toMatch(/never changes an answer/i);
        expect(text).toMatch(/ships no connector/i);
        expect(text).toMatch(/respondents never see/i);
        expect(text).not.toMatch(/scanned|safe to open/i);
    });

    it('covers reminders, export, due date policies, privacy, and audit search in operations', () =>
    {
        const text = articleText('request-operations');

        for (const phrase of ['Send reminders', 'Export CSV', 'Due date policies', 'Privacy', 'Audit search', 'Manage this request'])
        {
            expect(text).toContain(phrase);
        }
        expect(text).toMatch(/queued/i);
        expect(text).not.toMatch(/delivered to every party/i);
    });

    it('records privacy requests on the Privacy tab rather than through the API', () =>
    {
        const text = articleText('record-preservation');

        expect(text).toMatch(/Change scope/);
        expect(text).toMatch(/Privacy tab/);
        expect(text).not.toMatch(/through the API/i);
    });

    it('keeps Exchange Fields as Exchange metadata apart from request answers', () =>
    {
        const text = articleText('information-requests-overview');

        expect(text).toMatch(/Details tab/);
        expect(text).toMatch(/never turned into/i);
        expect(text).toMatch(/never changes them/i);
    });

    it('pins a request to the Version its Blueprint named when the request was created', () =>
    {
        const text = articleText('request-creating');

        expect(text).toMatch(/created afterwards/i);
        expect(text).toMatch(/Signer becomes Attestor/i);
        expect(text).toMatch(/not submitted answers/i);
    });

    it('explains request standings and that no plan change hides a recorded request', () =>
    {
        const text = articleText('information-requests-overview');

        for (const standing of ['Continuing as issued', 'Read only', 'Changes paused', 'Stopped'])
        {
            expect(text).toContain(standing);
        }
        expect(text).toMatch(/never hides a request/i);
        expect(text).toMatch(/Only people who manage the request see the owner's reason/i);
        expect(text).toMatch(/the tab says why/i);
    });

    it('keeps an issued workspace readable when its changes are paused or stopped', () =>
    {
        const text = articleText('request-access');

        expect(text).toMatch(/stays readable/i);
        expect(text).not.toMatch(/still stops access/i);
    });

    it('shows the operations, review, and record pages from the account rather than the viewer plan', () =>
    {
        const operations = articleText('request-operations');
        const review = articleText('request-review');
        const records = articleText('record-preservation');

        expect(operations).toMatch(/still owns requests/i);
        expect(operations).not.toMatch(/When your plan includes Information Requests/);
        expect(review).toMatch(/assigned request work/i);
        expect(review).not.toMatch(/When your plan includes Information Requests/);
        expect(records).toMatch(/stays readable/i);
    });

    it('describes when parties can be added and how a suspended trusted relationship shows', () =>
    {
        const text = articleText('request-creating');

        expect(text).toMatch(/Trusted relationship suspended/);
        expect(text).toMatch(/Removing a party is always possible/i);
        expect(text).toMatch(/only while the request is active/i);
    });

    it('shows the Information Requests settings tab only with the feature and says who authors Templates', () =>
    {
        const text = articleText('request-templates');

        expect(text).not.toMatch(/every signed-in user/i);
        expect(text).toMatch(/appears only when your plan or your active organization includes Information\s+Requests/i);
        expect(text).toMatch(/respond to and review/i);
        expect(text).toMatch(/active organization whose plan includes Information Requests/i);
    });

    it("states each plan's Information Request allowances and that an issued request keeps its own", () =>
    {
        const text = articleText('information-requests-overview');

        for (const allowance of ['25 open requests', '10 acting parties', '250 MiB', '5 GiB', '100 acting parties', '500 MiB', '100 GiB'])
        {
            expect(text).toContain(allowance);
        }
        expect(text).toMatch(/keeps the allowances it was issued with/i);
    });

    it('explains access link defaults and the limit on verification attempts', () =>
    {
        const text = articleText('request-access');

        expect(text).toMatch(/30 days/);
        expect(text).toMatch(/25 verifications/);
        expect(text).toMatch(/Too many verification attempts/);
    });

    it('explains the reminder cooldown, the daily export limit, and upload allowances', () =>
    {
        const operations = articleText('request-operations');
        const evidence = articleText('request-evidence');

        expect(operations).toMatch(/reminded in the last 24 hours/i);
        expect(operations).toMatch(/100 record exports a day/i);
        expect(evidence).toMatch(/evidence allowance it was issued with/i);
    });

    it('explains copying a platform Template before use and which Schemas a Template can use', () =>
    {
        const templates = articleText('request-templates');
        const creating = articleText('request-creating');

        expect(templates).toMatch(/copy it into My Templates or the organization/i);
        expect(templates).toMatch(/platform Schema/i);
        expect(templates).toMatch(/personal Fields and Schemas cannot be authored yet/i);
        expect(creating).toMatch(/copy a platform Template first/i);
        expect(creating).toMatch(/answers to that person's plan/i);
    });

    it("says Billing states the plan's Information Request allowances", () =>
    {
        expect(articleText('exchange-lifecycle')).toMatch(/Plans that include Information Requests also list their allowances/);
    });

    it('states that every Fields change names the version it read, with no last-save-wins route', () =>
    {
        const text = articleText('using-exchange-fields');

        expect(text).toMatch(/(states|stating) the version/i);
        expect(text).toMatch(/removing the schema/i);
        expect(text).toMatch(/If-Match/);
        expect(text).not.toMatch(/(last save wins|older route|older integration)/i);
    });
});
