import React from 'react';
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {AuthProvider} from './context/AuthContext';
import Exchanges from "./app/exchanges/Exchanges.tsx";
import RedirectIfNotAuthenticated from "./app/components/RedirectIfAuthenticated.tsx";
import IndividualOnboarding from "./app/onboarding/individual-onboarding/IndividualOnboarding.tsx";
import Settings from "./app/settings/Settings.tsx";
import NoAuthExchange from "./app/no-auth-exchange/NoAuthExchange.tsx";
import NoMenuProtectedRoute from "./app/components/NoMenuProtectedRoutes.tsx";
import OrganizationOnboarding from "./app/onboarding/organization-onboarding/OrganizationOnboarding.tsx";
import SignIn from "./app/authorization/sign-in/SignIn.tsx";
import AccountRecovery from "./app/authorization/account-recovery/AccountRecovery.tsx";
import SignUp from "./app/authorization/sign-up/SignUp.tsx";
import SignUpEmailConfirm from "./app/authorization/sign-up/SignUpEmailConfirm.tsx";
import ProtectedRoute from "./app/components/ProtectedRoutes.tsx";
import AppSessionExpired from "./app/app-session-expired/AppSessionExpired.tsx";
import {NotificationProvider} from "./context/NotificationContext.tsx";
import Home from "./app/home/Home.tsx";
import OAuthCallback from "./app/authorization/oauth/OAuthCallback.tsx";
import OAuthLinkConfirm from "./app/authorization/oauth/OAuthLinkConfirm.tsx";
import StepUpCompletion from "./app/authorization/step-up/StepUpCompletion.tsx";
import StepUpModal from "./app/components/step-up/StepUpModal.tsx";
import ThemeSync from "./app/components/ThemeSync.tsx";
import CapabilityProtectedContent from "./app/components/CapabilityProtectedContent.tsx";
import {Capability} from "./app/models/models.tsx";
import PlatformAdministration from "./app/platform-administration/PlatformAdministration.tsx";
import PlatformAudit from "./app/platform-audit/PlatformAudit.tsx";

const App: React.FC = () =>
{
    return (
        <BrowserRouter>
            <AuthProvider>
                <NotificationProvider>
                    <ThemeSync/>
                    <Routes>
                        <Route path="/"
                               element={
                                   <Home/>
                               }/>

                        <Route path="/sign-in"
                               element={
                                   <RedirectIfNotAuthenticated element={<SignIn/>}/>
                               }/>

                        <Route path="/sign-up"
                               element={
                                   <RedirectIfNotAuthenticated element={<SignUp/>
                                   }/>
                               }/>

                        <Route path="/sign-up/email-confirm"
                               element={
                                   <RedirectIfNotAuthenticated element={<SignUpEmailConfirm/>}/>
                               }/>

                        <Route path="/account-recovery"
                               element={
                                   <RedirectIfNotAuthenticated
                                       element={<AccountRecovery/>}/>
                               }/>

                        <Route path="/nas"

                               element={
                                   <RedirectIfNotAuthenticated
                                       element={<NoAuthExchange/>
                                       }/>
                               }/>

                        <Route path="/app-session-expired"
                               element={
                                   <RedirectIfNotAuthenticated element={<AppSessionExpired/>}/>
                               }/>

                        <Route path="/settings"
                               element={
                                   <ProtectedRoute path='/sign-in'
                                                   element={<Settings/>}/>
                               }/>

                        <Route path="/onboarding/individual"
                               element={
                                   <NoMenuProtectedRoute path='/sign-in'
                                                         element={<IndividualOnboarding/>}/>
                               }/>

                        <Route path="/onboarding/organization"
                               element={
                                   <ProtectedRoute path='/sign-in'
                                                   element={<OrganizationOnboarding/>}/>
                               }/>

                        <Route path="/exchanges"
                               element={
                                   <ProtectedRoute path='/sign-in'
                                                   element={<Exchanges/>}/>
                               }/>

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

                        <Route path="/oauth/callback"
                               element={<OAuthCallback/>}/>

                        <Route path="/oauth/link-confirm"
                               element={<OAuthLinkConfirm/>}/>

                        <Route path="/step-up-complete"
                               element={<StepUpCompletion/>}/>

                        <Route path="*"
                               element={<Navigate to="/" replace/>}/>
                    </Routes>
                    <StepUpModal/>
                </NotificationProvider>
            </AuthProvider>
        </BrowserRouter>
    );
};

export default App;
