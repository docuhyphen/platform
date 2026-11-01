import {Route} from "react-router-dom";
import ProtectedRoute from "../components/protected-routes/ProtectedRoutes.tsx";
import RecordPreservation from "../record-preservation/record-preservation-page/RecordPreservation.tsx";
import InformationRequestAssignedWork from "./assigned-work/assigned-work-page/InformationRequestAssignedWork.tsx";
import InformationRequestAuthorWorkspace from "./authoring/author-workspace/InformationRequestAuthorWorkspace.tsx";
import InformationRequestOperationsDetail from "./operations/operations-detail/InformationRequestOperationsDetail.tsx";
import InformationRequestOperations from "./operations/operations-page/InformationRequestOperations.tsx";
import InformationRequestRespondentWorkspace from "./respondent-workspace/InformationRequestRespondentWorkspace.tsx";
import InformationRequestReviewQueue from "./review/review-queue/InformationRequestReviewQueue.tsx";
import InformationRequestReviewWorkspace from "./review/review-workspace/InformationRequestReviewWorkspace.tsx";

export const informationRequestRoutes = (
    <>
        <Route path="/nir"
               element={
                   <InformationRequestRespondentWorkspace accessMode={"no-auth"}/>
               }/>

        <Route path="/information-requests/:requestId/respond"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestRespondentWorkspace accessMode={"authenticated"}/>}/>
               }/>

        <Route path="/information-requests/:requestId/manage"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestAuthorWorkspace/>}/>
               }/>

        <Route path="/information-requests/:requestId/reviews/:reviewId"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestReviewWorkspace/>}/>
               }/>

        <Route path="/information-request-reviews"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestReviewQueue/>}/>
               }/>

        <Route path="/information-request-assignments"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestAssignedWork/>}/>
               }/>

        <Route path="/information-request-operations"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestOperations/>}/>
               }/>

        <Route path="/information-request-operations/:requestId"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<InformationRequestOperationsDetail/>}/>
               }/>

        <Route path="/record-preservation"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={<RecordPreservation/>}/>
               }/>
    </>
);
