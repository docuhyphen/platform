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
            'blueprints', 'variables', 'fields', 'communications', 'document-library',
        ]);
        expect(HELP_DOC_ARTICLES.map(article => article.sectionId)).toEqual(
            getHelpDocSections().flatMap(section => section.articles.map(() => section.id)),
        );
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

    it('limits the concurrent-save protection to saves that state the version they read', () =>
    {
        const text = articleText('using-exchange-fields');

        expect(text).toMatch(/(states|stating) the version/i);
        expect(text).toMatch(/(last save wins|the last save|older integration)/i);
    });
});
