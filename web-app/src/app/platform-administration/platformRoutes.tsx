import {Route} from "react-router-dom";
import CapabilityProtectedContent from "../components/CapabilityProtectedContent.tsx";
import ProtectedRoute from "../components/protected-routes/ProtectedRoutes.tsx";
import {Capability} from "../models/models.tsx";
import PlatformAudit from "../platform-audit/PlatformAudit.tsx";
import PlatformAdministration from "./PlatformAdministration.tsx";

export const platformRoutes = (
    <>
        <Route path="/platform/administration"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={
                                       <CapabilityProtectedContent
                                           capability={Capability.APP_ADMIN}
                                           element={<PlatformAdministration/>}/>
                                   }/>
               }/>

        <Route path="/platform/audit"
               element={
                   <ProtectedRoute path='/sign-in'
                                   element={
                                       <CapabilityProtectedContent
                                           capability={Capability.APP_AUDIT_READ}
                                           element={<PlatformAudit/>}/>
                                   }/>
               }/>
    </>
);
