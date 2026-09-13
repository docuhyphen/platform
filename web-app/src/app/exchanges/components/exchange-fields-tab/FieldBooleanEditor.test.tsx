/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import FieldBooleanEditor from './FieldBooleanEditor';

afterEach(cleanup);

/** The dropdown trigger carries the reading it is currently showing as its own text. */
const trigger = (container: HTMLElement) =>
    container.querySelector<HTMLButtonElement>('#process-flag');

describe('FieldBooleanEditor', () =>
{
    it('shows an unanswered field as unanswered rather than as No', () =>
    {
        const {container} = render(
            <FieldBooleanEditor id="process-flag"
                                value={undefined}
                                onChange={vi.fn()}/>,
        );

        expect(trigger(container)?.textContent).toContain('Not answered');
    });

    it('shows each answer that was given', () =>
    {
        const {container: yes} = render(
            <FieldBooleanEditor id="process-flag"
                                value={true}
                                onChange={vi.fn()}/>,
        );
        expect(trigger(yes)?.textContent).toContain('Yes');

        cleanup();

        const {container: no} = render(
            <FieldBooleanEditor id="process-flag"
                                value={false}
                                onChange={vi.fn()}/>,
        );
        expect(trigger(no)?.textContent).toContain('No');
        expect(trigger(no)?.textContent).not.toContain('Not answered');
    });

    it('reports a chosen No as an answer', () =>
    {
        const onChange = vi.fn();
        const {container} = render(
            <FieldBooleanEditor id="process-flag"
                                value={undefined}
                                onChange={onChange}/>,
        );

        fireEvent.click(trigger(container) as HTMLButtonElement);
        fireEvent.click(screen.getByRole('option', {name: 'No'}));

        expect(onChange).toHaveBeenCalledWith(false);
    });

    it('reports a chosen Yes as an answer', () =>
    {
        const onChange = vi.fn();
        const {container} = render(
            <FieldBooleanEditor id="process-flag"
                                value={undefined}
                                onChange={onChange}/>,
        );

        fireEvent.click(trigger(container) as HTMLButtonElement);
        fireEvent.click(screen.getByRole('option', {name: 'Yes'}));

        expect(onChange).toHaveBeenCalledWith(true);
    });

    it('reports a cleared answer as unanswered', () =>
    {
        const onChange = vi.fn();
        render(
            <FieldBooleanEditor id="process-flag"
                                value={false}
                                onChange={onChange}/>,
        );

        fireEvent.click(screen.getByRole('button', {name: /clear/i}));

        expect(onChange).toHaveBeenCalledWith(null);
    });

    it('cannot be answered when the field is read-only', () =>
    {
        const {container} = render(
            <FieldBooleanEditor id="process-flag"
                                value={true}
                                disabled
                                onChange={vi.fn()}/>,
        );

        expect(trigger(container)?.disabled).toBe(true);
    });
});
