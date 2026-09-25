/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest';
import {DocumentDetailedDto} from '../../../models/models.tsx';
import ExchangeDocumentUploadDialog from './ExchangeDocumentUploadDialog.tsx';

const mocks = vi.hoisted(() => ({
    uploadExchangeDocument: vi.fn(),
    uploadDocumentVersion: vi.fn(),
}));

vi.mock('../../../../services/exchangeApi.ts', () => ({
    uploadExchangeDocument: (...args: unknown[]) => mocks.uploadExchangeDocument(...args),
    uploadDocumentVersion: (...args: unknown[]) => mocks.uploadDocumentVersion(...args),
}));

vi.mock('../../../../context/useToken.tsx', () => ({
    default: () => 'session-token',
}));

const requestedDocument = {
    id: 'requested-document',
    title: 'Process record',
} as DocumentDetailedDto;

const uploadedFile = () => new File(['process record content '.repeat(8)], 'process-record.pdf', {
    type: 'application/pdf',
});

describe('ExchangeDocumentUploadDialog', () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal('ResizeObserver', class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });
    beforeEach(() =>
    {
        mocks.uploadExchangeDocument.mockReset();
        mocks.uploadDocumentVersion.mockReset();
        mocks.uploadExchangeDocument.mockResolvedValue({...requestedDocument, uploadDate: '2026-09-24T10:00:00Z'});
    });
    afterEach(cleanup);

    it('uploads the file once and leaves recording the version to the upload itself', async () =>
    {
        const onDocumentUploaded = vi.fn();
        render(
            <ExchangeDocumentUploadDialog isOpen={true}
                                          exchangeId={'exchange-one'}
                                          exchangeDocument={requestedDocument}
                                          onDismiss={vi.fn()}
                                          onDocumentUploaded={onDocumentUploaded}/>,
        );

        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        fireEvent.change(input, {target: {files: [uploadedFile()]}});
        fireEvent.click(await screen.findByRole('button', {name: /^Upload$/}));

        await waitFor(() => expect(onDocumentUploaded).toHaveBeenCalledTimes(1));
        expect(mocks.uploadExchangeDocument).toHaveBeenCalledTimes(1);
        expect(mocks.uploadDocumentVersion).not.toHaveBeenCalled();
    });
});
