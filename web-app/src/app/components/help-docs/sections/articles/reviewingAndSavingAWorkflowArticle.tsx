export const reviewingAndSavingAWorkflowArticle = (
    <>
        <h3 id={"reviewing-and-saving-a-workflow-article-h3-6"}>Step 4: review warnings</h3>
        <p id={"reviewing-and-saving-a-workflow-article-p-5"}>
            The step list flags any step whose routes point at a step that no
            longer exists, and the save dialog repeats the routing warnings before
            you confirm. The designer also flags a hardcoded principal UUID in an
            assignee entry, which cannot be reused if the workflow is cloned, so
            replace them with{" "}
            <a id={"reviewing-and-saving-a-workflow-article-a-4"}
               href="#"
               data-help-article="assignees-and-portability">role-based or group-role-based assignees</a>.
        </p><h3 id={"reviewing-and-saving-a-workflow-article-h3-7"}>Step 5: set applicability (optional)</h3>
        <p id={"reviewing-and-saving-a-workflow-article-p-6"}>
            Open the <b id={"reviewing-and-saving-a-workflow-article-b-18"}>Applicability</b> card to restrict an organization workflow to Exchanges whose business field values match one or more conditions (leave empty to always run); see <a id={"reviewing-and-saving-a-workflow-article-a-5"}
                                                                                                                                                                                              href="#"
                                                                                                                                                                                                                                      data-help-article="workflow-applicability">field-based applicability</a>.
        </p><h3 id={"reviewing-and-saving-a-workflow-article-h3-8"}>Step 6: save</h3>
        <p id={"reviewing-and-saving-a-workflow-article-p-7"}>
            Click <b id={"reviewing-and-saving-a-workflow-article-b-19"}>Save workflow</b>. The workflow is created (POST) or updated
            (PUT) immediately. If the Active toggle is on, the engine will use it
            the next time the trigger event fires for an Exchange in your organization.
        </p>
        <p id={"reviewing-and-saving-a-workflow-article-p-8"}>
            Saving is rejected when the definition would fail at runtime (no steps, an Approval step with no assignees, a required approval count above its assignees, a step routing to itself or a missing step, a step that cannot reach End, or escalation without an SLA or targets). The message names the affected step.
        </p>
        <h3 id={"reviewing-and-saving-a-workflow-article-h3-9"}>Editing a live workflow</h3>
        <p id={"reviewing-and-saving-a-workflow-article-p-9"}>
            You cannot edit a workflow while it is still in progress for one or
            more Exchanges. The message lists the affected Exchanges so you know
            what still needs to finish or be cancelled before you try again.
        </p>
        <h3 id={"reviewing-and-saving-a-workflow-article-h3-10"}>Deleting a workflow</h3>
        <p id={"reviewing-and-saving-a-workflow-article-p-10"}>
            Delete deactivates the workflow (soft delete). It cannot be deleted
            while in-progress runs still use it; completed history is preserved.
        </p>
    </>
);
