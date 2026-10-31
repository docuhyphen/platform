/** @vitest-environment jsdom */
import {readdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {afterEach, describe, expect, it} from 'vitest';
import {cleanup, render} from '@testing-library/react';
import {getHelpDocArticleById, getHelpDocSections, HELP_DOC_ARTICLES} from './helpDocsRegistry';

const HELP_DOCS_DIR = join(process.cwd(), 'src', 'app', 'components', 'help-docs');
const SECTIONS_DIR = join(HELP_DOCS_DIR, 'sections');
const ARTICLES_DIR = join(SECTIONS_DIR, 'articles');

const lineCount = (path: string): number =>
{
    const text = readFileSync(path, 'utf8');
    return text === '' ? 0 : text.replace(/\n$/, '').split('\n').length;
};

const articleText = (...articleIds: string[]): string =>
    articleIds.map(articleId => {
        const article = getHelpDocArticleById(articleId);
        if (!article) throw new Error(`No help article ${articleId}`);
        const {container} = render(<>{article.content}</>);
        return (container.textContent ?? '').replace(/\s+/g, ' ');
    }).join(' ');

afterEach(cleanup);

describe('help documentation registry', () =>
{
    it('claims no Manage control the page does not offer and points no author at the API', () => {
        const managing = articleText('request-creating', 'request-issuing');
        const outcomes = articleText('request-outcomes');

        expect(managing).not.toMatch(/POST \/information-requests/);
        expect(managing).toMatch(/No screen offers amendments yet\./);
        expect(managing).toMatch(/a refused change keeps its dialog open with what you entered\. Refresh, and issuing, cancelling or replacing the request, read every panel again\./);
        expect(managing).toMatch(/A preview that cannot be read offers Try again\./);
        expect(managing).toMatch(/The clock key is a lowercase key that starts with a letter or digit, and a stopped clock says Stopped; no active deadline\./);
        expect(managing).not.toMatch(/reassign their party to a recipient of the Exchange/);
        expect(managing).toMatch(/reassign their party to a person with an account or a trusted recipient, or remove the party and add them again by the email of their account/);
        expect(outcomes).not.toMatch(/A fact can replace a current fact/);
        expect(outcomes).toContain("An answer that cannot be promoted says why instead of offering Promote: the request names no single subject, the subject was named after the submission, the subject's processing is restricted, the answer was corrected, or its review has not accepted it yet. An answer already kept as a fact names its purposes; promoting it again for one of them is refused.");
        expect(outcomes).toMatch(/When the request names several subjects, choose the subject who asked\. Each answer lists the corrections already recorded for it, with the subject, the time, and the reason\./);
        expect(outcomes).toContain('Promoting never replaces a current fact from another request: when two current facts about the same subject differ, both are marked as differing until one of them is revoked in its own request.');
    });

    it('names the Exchange tab actions, the reviewer path and the list retry', () => {
        const overview = articleText('information-requests-overview');

        expect(overview).toContain('When you also manage a request whose next action is Respond or Review, Manage is offered beside it.');
        expect(overview).toContain('A reviewer who only reviews opens their latest review with View.');
        expect(overview).toContain('if the list cannot be loaded the tab stays, says so and offers Try again.');
    });

    it('tells a respondent whose link lost its token what to do, and states file limits in binary units', () => {
        expect(articleText('request-access')).toContain('the page says the link is incomplete: open the link you were sent again, in full.');
        expect(articleText('request-evidence')).toContain('By default a file can be up to 25 MiB when signed in and 10 MiB through an access link.');
    });

    it('states that the review decision history counts remediations instead of listing each correction', () => {
        const review = articleText('request-review', 'request-review-findings');

        expect(review).toContain('The decision history lists the review\'s decisions and counts its remediations; it does not show each correction in detail.');
        expect(review).not.toContain('lists every decision and correction');
    });

    it('says Exchange access and request parties are separate and what a refused approved ending leaves', () => {
        expect(articleText('request-access')).toContain('does not remove them from its Information Requests: remove or reassign their party on each request.');
        expect(articleText('request-creating', 'request-issuing')).toContain('the Exchange stays active, you and the approvers are notified, and ending it again needs a new approval.');
    });

    it('says the copy dialog explains the Schema rule whenever a copy goes to another owner', () => {
        expect(articleText('request-template-versions')).toContain('the dialog explains this whenever the copy goes to another owner.');
    });

    it('says Add is shown only where the respondent may add an entry', () => {
        expect(articleText('request-submission', 'request-submission-confirmations')).toContain('Adding an entry needs permission to answer its items, and Add is shown only where you have it;');
    });

    it('names the visible clock start controls in managing help', () => {
        expect(articleText('request-creating', 'request-issuing')).toContain('Choose Due policy, Urgency, and Clock key, then Start clock.');
    });
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
            'information-requests-overview', 'request-templates', 'request-template-publishing', 'request-template-versions',
            'request-creating', 'request-issuing', 'request-due-dates', 'request-access', 'request-submission',
            'request-submission-confirmations', 'request-evidence', 'request-review', 'request-review-findings',
            'request-outcomes', 'request-external-sources', 'request-operations', 'request-operations-records',
            'record-preservation',
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

    it('says a read-only field is filled only from the default value set in the schema editor', () =>
    {
        const text = articleText('using-exchange-fields');
        const overview = articleText('fields-overview');

        expect(text).toMatch(/read.only/i);
        expect(text).toMatch(/Default value/);
        expect(overview).toMatch(/Default value/);
        expect(text).not.toMatch(/stays empty/i);
        expect(overview).not.toMatch(/stays empty/i);
    });

    it('says the Details tab offers schema and value controls only to callers allowed to use them', () => {
        const text = articleText('using-exchange-fields');

        expect(text).toMatch(/Remove schema/);
        expect(text).toMatch(/your own plan/i);
        expect(text).not.toMatch(/registered application/i);
        expect(text).not.toMatch(/application,/i);
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
        const submission = articleText('request-submission', 'request-submission-confirmations');
        const review = articleText('request-review', 'request-review-findings');

        expect(submission).not.toMatch(/refused at issuance until review is available/i);
        expect(submission).toMatch(/waits for review/i);
        expect(review).toMatch(/Nothing takes effect until you select Record decisions/);
        expect(review).toMatch(/only the items marked Changes required/i);
        expect(review).not.toMatch(/Exchange.s fields are updated/i);
    });

    it('lets owners and administrators see the whole queue while request details stay with the people who manage each request', () =>
    {
        const text = articleText('request-operations', 'request-operations-records');

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
        const text = articleText('request-templates', 'request-template-publishing');

        expect(text).toMatch(/Sections, Groups, Conditions, Review, and Settings/);
        expect(text).toMatch(/Before publishing/);
        expect(text).not.toMatch(/not available in this deployment/i);
        expect(text).not.toMatch(/choose the Field that will collect the answer/i);
    });

    it('says archived and cleared conditional answers return without their earlier files', () => {
        const text = articleText('request-templates', 'request-template-publishing');

        expect(text).toMatch(/Retaining keeps hidden answers and their files/i);
        expect(text).toMatch(/Archiving copies a hidden answer, with the versions of its\s+files/i);
        expect(text).toMatch(/return as empty, without their earlier files/i);
    });

    it('explains creating, issuing, and managing a request from its Exchange', () =>
    {
        const text = articleText('request-creating', 'request-issuing');

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
        const text = articleText('request-submission', 'request-submission-confirmations');

        expect(text).toMatch(/two seconds/i);
        expect(text).toMatch(/Save responses/);
        expect(text).toMatch(/Answers saved elsewhere shows its latest saved answer/);
        expect(text).toMatch(/Keep saved answer/);
        expect(text).toMatch(/Unsaved changes stay in the tab until saved/);
        expect(text).toMatch(/Review and submit/);
    });

    it('says a partly provided answer gives its reason and an edit keeps the answer chosen', () => {
        const text = articleText('request-submission', 'request-submission-confirmations');

        expect(text).toMatch(/partly provided answer keeps its value too/);
        expect(text).toMatch(/Changing a value or a reason later keeps the answer you chose/);
    });

    it('says a provided answer takes an optional note that keeps an earlier reason', () => {
        const text = articleText('request-submission', 'request-submission-confirmations');

        expect(text).toMatch(/An item can also take a note: a provided answer has an optional Note, which keeps any reason/);
    });

    it('says the summary before submitting states each answer with its reason or note', () => {
        expect(articleText('request-submission', 'request-submission-confirmations')).toMatch(
            /Before you submit, each answer in the part you submit is stated in words, with its reason or note, a choice by its label, and a document by the number of files attached to it\./,
        );
        expect(articleText('request-submission', 'request-submission-confirmations')).toMatch(/nothing is still saving or sending, and it updates as soon as a file is uploaded, replaced, or withdrawn/);
        expect(articleText('request-submission', 'request-submission-confirmations')).toMatch(/It says why when an answer was not saved/);
    });

    it('says how a respondent keeps an amended answer, confirms as one party, and sees a submitted answer read-only', () => {
        const text = articleText('request-submission', 'request-submission-confirmations');

        expect(text).toMatch(/select Keep this answer to keep it as it is/);
        expect(text).toMatch(/choose the one you confirm for under Confirm as/);
        expect(text).toMatch(/each of its answers is shown read-only with the reason/);
        expect(text).toMatch(/A submission that answers a correction cannot be withdrawn/);
    });

    it('says who can answer a Field that is not Public and that a read-only Field cannot be collected', () => {
        expect(articleText('request-templates', 'request-template-publishing')).toMatch(/Publishing refuses a typed answer whose Field the Schema marks read-only/);
        expect(articleText('request-templates', 'request-template-publishing')).toMatch(/can be answered only by people inside the request's owner/);
        expect(articleText('request-creating', 'request-issuing')).toMatch(/a role that answers a Field not marked Public is refused/);
    });

    it('says a single choice from a list can be cleared like a Yes or No answer', () => {
        expect(articleText('using-exchange-fields'))
            .toMatch(/A field with one choice from a list has the same clear control; a field with several choices is cleared by deselecting each of them\./);
    });

    it('says an interrupted upload is kept for a retry', () =>
    {
        expect(articleText('request-evidence')).toMatch(/Retry/);
    });

    it('says an interrupted record export, appeal, or reused answer is sent again as the same command', () => {
        expect(articleText('request-operations', 'request-operations-records')).toMatch(/If Create export is interrupted, select it again: .* one export is made/);
        expect(articleText('request-review', 'request-review-findings')).toMatch(/select Send appeal again: the appeal is recorded once/);
        expect(articleText('request-submission', 'request-submission-confirmations')).toMatch(/Use this answer, which saves it as your answer\. If saving is interrupted, select it again: the answer is saved once/);
    });

    it('says an interrupted save, entry change, submission, confirmation, or withdrawal is recorded once when selected again', () => {
        const submission = articleText('request-submission', 'request-submission-confirmations');

        expect(submission).toMatch(
            /If saving, or adding, removing, or moving an entry, is interrupted, select it again: it is sent as the same change and recorded once, also after you verify again\./,
        );
        expect(submission).toMatch(
            /If submitting, a confirmation, or Withdraw is interrupted, select it again: it is recorded once, also after you verify again\./,
        );
        expect(articleText('request-evidence')).toMatch(
            /If withdrawing is interrupted, select it again with the same reason: the file is withdrawn once\./,
        );
    });

    it('says unsent answers belong to the party who entered them and move to the account the party links', () => {
        const text = articleText('request-access');

        expect(text).toMatch(/Answers not yet saved belong to the party who entered them, not to the browser tab\./);
        expect(text).toMatch(
            /Another party's access link or another account opened in the same tab neither shows nor saves them, and a save the platform finds was entered by someone else is refused with nothing saved; they stay in the tab for the person who entered them\./,
        );
        expect(text).toMatch(/Linking your account moves them to that account\./);
    });

    it('says everything shown for an earlier respondent closes once another is found responding in the tab', () => {
        const text = articleText('request-access');

        expect(text).toMatch(
            /When the workspace finds that another party or account is now responding in the tab, everything it showed for the earlier one closes, including From your previous submission, earlier accepted answers, What changed in this request, Review results, Review and submit, an open dialog, and a file kept to send again\./,
        );
        expect(text).toMatch(/It then shows only what the current respondent may see, and nothing still arriving for the earlier one is shown or opened\./);
        expect(text).toMatch(/A file kept to send again stays with the party who chose it, like unsaved answers\./);
        expect(articleText('request-evidence')).toMatch(
            /It stays with the party who chose it: another party or account responding in the tab does not see it\./,
        );
    });

    it('says a preview or download still being read is not opened once the page is left, another respondent is found, or its file is no longer shown', () => {
        expect(articleText('request-evidence')).toMatch(
            /A preview or download that is still being read when you leave the page, when another party or account is found responding in the tab, or once the Requirement no longer shows that file, for example because its files are hidden or a newer version replaced it, is not opened\. Each download and preview is recorded\./,
        );
    });

    it('says a file refused because its access-link session ended asks to verify again', () => {
        expect(articleText('request-evidence')).toMatch(
            /Through an access link, a file is released only if its session is still current once the file has been read and, for a protected question, still verified\. If the session has ended, the page asks you to verify again\./,
        );
    });

    it('says a refused submission summary shows nothing from before, and a chosen part stays chosen until it loads', () => {
        const text = articleText('request-submission', 'request-submission-confirmations');

        expect(text).toMatch(/If the summary cannot be loaded, .* it shows nothing from before and says why; select Try again to load it again\./);
        expect(text).toMatch(/The part you choose stays chosen while your answers save, and Submit waits until that part has loaded\./);
        expect(text).toMatch(/select Withdraw on its submission/);
    });

    it('says an interrupted account link is selected again and returns the link already made', () => {
        expect(articleText('request-access')).toMatch(
            /If linking is interrupted, select Link to my account again: an account link already made is returned/,
        );
    });

    it('says an interrupted account link can still be finished after the page asks to verify again', () => {
        expect(articleText('request-access')).toMatch(
            /If the page asks you to verify again after an interrupted link, Link to my account stays there: select it to finish the link\. If the link was not made, it then disappears and you verify again\./,
        );
    });

    it('says opening another request starts its operations page afresh', () => {
        expect(articleText('request-operations', 'request-operations-records'))
            .toMatch(/Opening another request, including with the browser's Back button, starts on its Clocks tab and the first page of its audit history\. Nothing shown or drafted for the earlier request carries over/);
    });

    it('says a list that can no longer be read stops showing what it read before', () => {
        expect(articleText('request-operations', 'request-operations-records'))
            .toMatch(/When the queue, an audit search, or a tab can no longer be read, for example because your access changed, the page says why and stops showing what it read before until it can be read again\./);
    });

    it('says the queue withholds the names it learned for Assigned to after a refused read', () => {
        expect(articleText('request-operations', 'request-operations-records'))
            .toMatch(/Until then, Assigned to offers only Anyone and Me, and a party you already chose there shows as Chosen party\./);
    });

    it('says switching workspace shows only that workspace and stops an export or download made for the earlier one', () => {
        expect(articleText('request-operations', 'request-operations-records')).toMatch(
            /Switching to another organization or to your personal workspace does the same for the whole page: it shows only what that workspace holds, and an export or download still being made for the earlier one stops without saving a file\./,
        );
    });

    it('says a record download still being read is not saved once its request, tab, or privacy records are no longer shown', () => {
        expect(articleText('request-operations', 'request-operations-records')).toMatch(
            /A download still being read when you open another request or leave the Records tab is not saved\./,
        );
        expect(articleText('record-preservation')).toMatch(
            /A record still being read when you leave the Privacy tab or switch workspace, or once the privacy records can no longer be read, is not saved\./,
        );
    });

    it('says the daily record export ceiling counts all of an owner\'s requests, also for exports made at once', () => {
        expect(articleText('request-operations', 'request-operations-records')).toMatch(
            /Each owner can make up to 100 record exports a day, subject exports included, across all of its requests, also when several are made at once; the next export then says when to try again\./,
        );
    });

    it('says anyone the review shows an item to can open its submitted files', () => {
        expect(articleText('request-review', 'request-review-findings')).toContain("Anyone the review shows an item to can also open that item's submitted files.");
    });

    it('says a submitted file still being read is not opened once the reviewer leaves the review or it is no longer shown', () => {
        expect(articleText('request-review', 'request-review-findings')).toMatch(
            /Preview and Download open the exact file version that was submitted, even after its submission is withdrawn or its entry removed\. Preview opens inside the review; close it to return\. A file still being read when you leave the review, or once the review no longer shows it, is not opened\./,
        );
    });

    it('says record preservation shows only the records of the workspace switched to', () => {
        expect(articleText('record-preservation')).toMatch(
            /Switching to another organization or to your personal workspace shows only that workspace's holds, retention schedule, and disposals\./,
        );
    });

    it('says every operations list that cannot be read offers Try again', () => {
        expect(articleText('request-operations', 'request-operations-records')).toMatch(/Each list that cannot be read offers Try again/);
        expect(articleText('record-preservation')).toMatch(/cannot be read, the page says why and offers Try again\./);
    });

    it('says which requests take reminders, what the queue and its CSV state, and what an export hash covers', () => {
        const operations = articleText('request-operations', 'request-operations-records');
        const records = articleText('record-preservation');

        expect(operations).toMatch(/Select issued or in progress requests, up to 100 at a time/);
        expect(operations).toMatch(/Drafts and finished requests take no reminder/);
        expect(operations).toMatch(/Changing a filter returns to the first page and clears the selection\./);
        expect(operations).toMatch(/when it was issued, first viewed, and started/);
        expect(operations).toMatch(/The hash covers the stored record, so a saved file's own checksum differs from it\./);
        expect(operations).toMatch(/Each result offers This actor and This request/);
        expect(records).toMatch(/These pages place holds on requests only\./);
        expect(records).toMatch(/History on a hold lists each of these with who made it, when, and why/);
    });

    it('says a Requirement whose files cannot be read hides them while a kept file stays', () => {
        expect(articleText('request-evidence')).toMatch(
            /If a Requirement's files can no longer be read, for example after your verification lapses, its files and their actions are hidden and the page says why; select Try again to read them again\. A file kept for Retry stays on the page and can be sent once its files are shown again\./,
        );
    });

    it('says clocks that cannot be read are hidden with their controls until they are read again', () => {
        expect(articleText('request-creating', 'request-issuing')).toMatch(/When clocks cannot be read, they and their controls are hidden; select Try again\./);
    });

    it('says a change, page, or file sent as its access-link session ends is refused rather than saved or shown', () => {
        expect(articleText('request-access')).toMatch(
            /A change is saved only while its session is still current: one sent as the session ends is refused and nothing of it is saved\. A page you read, or a file you download or preview, reaches you only if the session is still current once it has been read\./,
        );
    });

    it('says a file opened through an access link is released only while its session and verification still hold', () => {
        expect(articleText('request-evidence')).toMatch(
            /Through an access link, a file is released only if its session is still current once the file has been read and, for a protected question, still verified\./,
        );
    });

    it('says a protected question cannot be changed or shown once its verification time has passed', () => {
        expect(articleText('request-access')).toMatch(
            /After that, a change to a protected question is refused and nothing of it is saved, even if the question was still on screen, and a page read as that time passes shows it hidden\./,
        );
    });

    it('says a late result from an earlier link, session, or page of the same request keeps the last verified session', () => {
        expect(articleText('request-access')).toMatch(
            /After you open a newer link for the same request, or verify again once a session ended, the session you verified last is kept: a verification or session end that arrives late from the earlier link or session, or from a page you have left, neither replaces nor ends it\./,
        );
    });

    it('says leaving the request while it is being linked keeps you where you went', () => {
        expect(articleText('request-access')).toMatch(
            /If you leave the request before linking finishes, you stay on the page you went to; if the link was made, you then open the request from your account\./,
        );
    });

    it('says a notice for a reassigned party is not sent and is never redirected to the new holder', () => {
        expect(articleText('request-operations', 'request-operations-records')).toMatch(
            /A notice whose party was removed or reassigned to someone else, whose request has finished \(other than the overdue notice owed by the due time that expired it\), or whose Exchange was rejected, rescinded, or deleted is not sent: it is marked undeliverable with that reason, and it is never redirected to the new holder\./,
        );
    });

    it('says a stage that keeps responders apart also counts the account linked to a responder whose party was reassigned', () => {
        expect(articleText('request-review', 'request-review-findings')).toMatch(
            /and the account linked to anyone who answered, submitted, or confirmed, even after that person's party was reassigned\./,
        );
    });

    it('covers reviewer assignment, recusal, delegation, overrides, and reconsideration', () =>
    {
        const text = articleText('request-review', 'request-review-findings');

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
        expect(articleText('request-submission', 'request-submission-confirmations')).toContain('Use this answer');
    });

    it('explains imported values, reconciliation, connectors, and generated outputs as records that never change an answer', () =>
    {
        const text = articleText('request-external-sources');

        for (const phrase of [
            '/information-requests/{id}/imported-values', '/imported-value-reconciliations',
            '/imported-value-discrepancies/{discrepancyId}/resolutions', '/connector-exchanges', '/generated-outputs',
            'MATCHES', 'DIFFERS', 'NOT_COMPARABLE', 'FIELD_CHANGED', 'RESPONSE_STANDS', 'FOLLOW_UP_REQUESTED',
        ])
        {
            expect(text).toContain(phrase);
        }
        expect(text).toMatch(/never compared with an answer to the\s+new Field/i);
        expect(text).toMatch(/never changes an answer/i);
        expect(text).toContain(
            "A value whose Requirement an amendment took out of the request stays recorded with its source, provenance, decision, and discrepancies, but a reconciliation leaves it out while the request's current Version does not ask for that Requirement.",
        );
        expect(text).toMatch(/ships no connector/i);
        expect(text).toMatch(/respondents never see/i);
        expect(text).not.toMatch(/scanned|safe to open/i);
    });

    it('keeps each connector call, wait, retry, and ending in the request audit history as a change made by the system', () => {
        expect(articleText('request-external-sources')).toContain(
            "The request's audit history records each call, each wait the connector asks for, each retry, and how the lookup ended, with its failure code when it failed, as a change made by the system. A step that cannot be recorded is not applied, and the lookup is tried again later.",
        );
        expect(articleText('request-operations', 'request-operations-records')).toContain('or the system (clocks, notices, file checks, and connectors)');
    });

    it('covers reminders, export, due date policies, privacy, and audit search in operations', () =>
    {
        const text = articleText('request-operations', 'request-operations-records');

        for (const phrase of ['Send reminders', 'Export CSV', 'Due date policies', 'Privacy', 'Audit search', 'Manage this request'])
        {
            expect(text).toContain(phrase);
        }
        expect(text).toMatch(/queued/i);
        expect(text).not.toMatch(/delivered to every party/i);
        expect(text).toContain(
            "Export CSV downloads every request that matches the current filters, as the queue shows it, with its due, created, issued, first viewed, and started times in UTC; Due is empty when no clock is running, and the others until they happen.",
        );
        expect(text).toContain(
            "Each change publishes a new version that starts from the latest one, working hours, Communications, and escalation included. Leave Escalate after empty to never escalate; 0 escalates at the due time.",
        );
        expect(text).toContain(
            "Business hours count only the periods under Working hours, skipping holidays; a day can have several periods, days can have different hours, and a period set to Until midnight ends with its day.",
        );
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
        const text = articleText('request-creating', 'request-issuing');

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
        expect(text).toContain('Managers can still remove a party, revoke an access link or an accepted fact, pause or extend a clock, and cancel or replace the request, but cannot add a party, create or resend a link, or start or resume a clock.');
    });

    it('says outcomes cannot be recorded on a request whose changes are paused or that was stopped', () => {
        const text = articleText('request-outcomes');

        expect(text).toContain('so neither is offered on a request marked Changes paused or Stopped, nor once the Exchange is no longer active.');
    });

    it('says which ending controls a stopped, paused, or read-only request still offers its owner', () => {
        const text = articleText('request-creating', 'request-issuing');

        expect(text).toContain('It can be changed until the request finishes, also on a Stopped request, but not on one marked Changes paused; on a Read only draft it can only be turned off.');
        expect(text).toContain('Both remain available to the owner of a request marked Stopped, and neither to the owner of one marked Changes paused. A draft marked Read only can still be cancelled, but not superseded.');
    });

    it('keeps an issued workspace readable when its changes are paused or stopped', () =>
    {
        const text = articleText('request-access');

        expect(text).toMatch(/stays readable/i);
        expect(text).not.toMatch(/still stops access/i);
    });

    it('shows the operations, review, and record pages from the account rather than the viewer plan', () =>
    {
        const operations = articleText('request-operations', 'request-operations-records');
        const review = articleText('request-review', 'request-review-findings');
        const records = articleText('record-preservation');

        expect(operations).toMatch(/still owns requests/i);
        expect(operations).not.toMatch(/When your plan includes Information Requests/);
        expect(review).toMatch(/whatever your own plan/i);
        expect(review).toMatch(/Reviews assigned to you/);
        expect(review).toMatch(/Information Requests tab/);
        expect(review).not.toMatch(/My reviews/);
        expect(review).not.toMatch(/When your plan includes Information Requests/);
        expect(records).toMatch(/stays readable/i);
    });

    it('says how ending a request or its Exchange ends access, and which conditions a Template refuses', () => {
        const access = articleText('request-access');
        const managing = articleText('request-creating', 'request-issuing');
        const templates = articleText('request-templates', 'request-template-publishing');

        expect(access).toMatch(/no new verification code or session is issued for it, and a session already open keeps reading it until the session expires/);
        expect(access).toMatch(/rejecting, rescinding, or deleting its Exchange ends its access links and sessions at once/);
        expect(managing).toMatch(/its access links stop working/);
        expect(templates).toMatch(/one that would clear or archive the answer of a role other than the one answering what it reads/);
        expect(templates).toMatch(/A condition never clears or archives another party's answer or one a submission holds/);
        expect(templates).toMatch(/section, item, group, condition, or review stage/);
    });

    it('says where creation sources and due date policies come from, when clocks wait, and what superseding needs', () => {
        const managing = articleText('request-creating', 'request-issuing');
        const operations = articleText('request-operations', 'request-operations-records');

        expect(managing).toMatch(/Templates, Blueprints, and Schemas are offered only while you work in the workspace that owns the Exchange/);
        expect(managing).toMatch(/no clock changes while the owner's account is suspended/);
        expect(managing).toMatch(/Due date policies are set up in operations and offered only in the workspace that owns the request/);
        expect(managing).toMatch(/another request on the same Exchange that is not finished, a draft or an issued one you manage too, that replaces it/);
        expect(managing).toMatch(/Earlier requests and follow-ups are listed only when you can open them, and the next request's due time only when you can open every request its schedule has created; if they cannot be read, select Try again/);
        expect(operations).toMatch(/takes effect, with its overdue notice, at the first check after the suspension ends; until then no clock can be paused, resumed, or extended either/);
        expect(operations).toMatch(/whose request has finished \(other than the overdue notice owed by the due time that expired it\)/);
    });

    it('says when clocks wait for acceptance, pause after a complete submission, and whom they remind', () => {
        const dueDates = articleText('request-due-dates');

        expect(dueDates).toMatch(/Once the Exchange is accepted, a clock started without a received time counts its whole time from the acceptance/);
        expect(dueDates).toMatch(/They resume with the time they had left when work goes back to the respondents: an item is returned for correction, or the submission is withdrawn\. A clock you paused yourself stays paused until you resume it\./);
        expect(dueDates).toMatch(/go only to the responding parties who still have items to provide/);
        expect(dueDates).toMatch(/A notice owed to a group goes to each active member with an email address/);
    });

    it('says audit history and record exports name a related request only to people who can open it', () => {
        const operations = articleText('request-operations', 'request-operations-records');

        expect(operations).toMatch(/A follow-up or replacement request is named only when you can open it; otherwise it is withheld and counted too\./);
        expect(operations).toMatch(/An export names the request that replaced this one, or the request behind an accepted fact it reused, only when its maker could open that request, and Download also leaves out any that you cannot open\./);
    });

    it('keeps New Template offered at the open request limit and leaves file removal to the API', () => {
        const templates = articleText('request-templates', 'request-template-publishing');
        const evidence = articleText('request-evidence');

        expect(templates).toMatch(/reaching the open request limit does not stop Template authoring/i);
        expect(evidence).toMatch(/removing a file is available only through the API/i);
        expect(evidence).not.toMatch(/can still remove a file/i);
    });

    it('describes when parties can be added and how a suspended trusted relationship shows', () =>
    {
        const text = articleText('request-creating', 'request-issuing');

        expect(text).toMatch(/Trusted relationship suspended/);
        expect(text).toMatch(/Until a request finishes, removing a party is always possible/i);
        expect(text).toMatch(/start only once the request is issued and remain available after it closes/i);
    });

    it('shows the Information Requests settings tab only with the feature and says who authors Templates', () =>
    {
        const text = articleText('request-templates', 'request-template-publishing');

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
        const operations = articleText('request-operations', 'request-operations-records');
        const evidence = articleText('request-evidence');

        expect(operations).toMatch(/reminded in the last 24 hours/i);
        expect(operations).toMatch(/100 record exports a day/i);
        expect(evidence).toMatch(/evidence allowance it was issued with/i);
    });

    it('explains choosing a Version, retiring the last one, and what a copy keeps', () => {
        const versions = articleText('request-template-versions');

        expect(versions).toMatch(/Version to act on/);
        expect(versions).toMatch(/with no draft open, it ends the Template/);
        expect(versions).toMatch(/All versions retired/);
        expect(versions).toMatch(/its description, which starts as the\s+source's/);
        expect(versions).toMatch(/works only when that Schema is a Platform Schema/);
    });

    it('explains copying a platform Template before use and which Schemas a Template can use', () =>
    {
        const templates = articleText('request-templates', 'request-template-publishing');
        const creating = articleText('request-creating', 'request-issuing');

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

    it('says scheduled clock points and notices wait while the owner is suspended and stop for a revoked grant', () => {
        const operations = articleText('request-operations', 'request-operations-records');

        expect(operations).toMatch(/While the owner's account is suspended, a running clock records no reminder, overdue notice, escalation, or expiry/);
        expect(operations).toMatch(/a due time that passed meanwhile takes effect, with its overdue notice, at the first check after the suspension ends/);
        expect(operations).toMatch(/execution grant is revoked, or when it does not allow reminders, its clocks record none of these again/);
        expect(operations).toMatch(/a notice, or a retry, that comes due while the owner's account is suspended/);
    });

    it('says a member who joins a group under suspended trust cannot act for it and that Delegate names each candidate', () => {
        expect(articleText('request-access')).toMatch(
            /a member who joins the group cannot act for it or open the request through it until trust is restored; members who could act before keep working/,
        );
        expect(articleText('request-review', 'request-review-findings')).toMatch(/Delegate lists by name each other Reviewer party the stage still admits, and is not offered when there is none/);
    });

    it('says what a request page still offers once its Exchange stops', () => {
        const overview = articleText('information-requests-overview');
        const managing = articleText('request-creating', 'request-issuing');
        const outcomes = articleText('request-outcomes');

        expect(overview).toMatch(/Once the Exchange stops, a request's management page offers only the actions that are still allowed/);
        expect(overview).toMatch(/no new outcome or follow-up request can be recorded; the owner can still revoke accepted facts/);
        expect(overview).toMatch(/who can no longer change anything on it, not even an accepted fact/);
        expect(managing).toMatch(/Preview for\. It is offered until the request finishes or its Exchange stops\. Issue opens it/);
        expect(managing).toMatch(/as long as the owner's plan allows new requests and the Exchange has not stopped/);
        expect(managing).toMatch(/Under Expiry refreshes/);
        expect(outcomes).toMatch(/nor once the Exchange is no longer active/);
        expect(outcomes).toMatch(/it is no longer offered once the Exchange is deleted/);
    });

    it('says a draft can be superseded and take its follow-up schedule before it is issued', () => {
        const managing = articleText('request-creating', 'request-issuing');

        expect(managing).toMatch(/Supersede, on a draft or an issued request, names another request on the same Exchange/);
        expect(managing).toMatch(/the schedule can be set while it is still a draft; once it is issued, select Create the next request when it is time/);
        expect(managing).toMatch(/then Add refresh rule, on a draft too; each document takes one rule/);
        expect(managing).toMatch(/they start only once the request is issued and remain available after it closes/);
        expect(managing).not.toMatch(/Once issued, a request can repeat/);
        expect(managing).toMatch(/Every request of a series shows it and offers Create the next request while the series can continue, even after its first request was cancelled or replaced/);
        expect(managing).toMatch(/Each rule says from which day its refresh is due or that it was made, and Refresh now is offered only while due/);
    });

    it('describes how the response workspace names refusals, holds unexplained answers, guards entries, and keeps unsent files', () => {
        const submission = articleText('request-submission', 'request-submission-confirmations');
        const evidence = articleText('request-evidence');

        expect(submission).toMatch(/When a save is refused, the message names each affected answer, with a link to it, and how many others it cannot show you; after Save responses, focus moves to the first one, or to the clears to confirm\./);
        expect(submission).toMatch(/The reason is required: such an answer is saved once its reason is typed, while your other answers keep saving\./);
        expect(submission).toMatch(/The delete button on an entry asks you to confirm, then removes the entry with the entries nested in it\./);
        expect(submission).toMatch(/Entries are numbered by their place in their list/);
        expect(submission).toMatch(/a section holding only a confirmation says to confirm under Review and submit/);
        expect(submission).not.toMatch(/Remove occurrence/);
        expect(evidence).toMatch(/the file and its details are kept on the page: select Retry to send it again against the latest files/);
        expect(evidence).toMatch(/If your browser blocks the tab, the page says so: allow pop-ups for this site, or select Download\./);
        expect(evidence).toMatch(/a linked answer names the document that supports it, and the document names each answer it supports/);
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
