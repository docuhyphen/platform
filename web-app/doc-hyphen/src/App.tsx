import React from 'react';
import {BrowserRouter, Route, Routes} from 'react-router-dom';
import {AuthProvider} from './context/AuthContext';
import SharingSessions from "./app/sharing-sessions/SharingSessions.tsx";
import RedirectIfNotAuthenticated from "./app/components/RedirectIfAuthenticated.tsx";
import NotFound from './app/NotFound.tsx';
import IndividualOnboarding from "./app/onboarding/individual-onboarding/IndividualOnboarding.tsx";
import Settings from "./app/settings/Settings.tsx";
import NoAuthSharingSession from "./app/no-auth-sharing-session/NoAuthSharingSession.tsx";
import NoMenuProtectedRoute from "./app/components/NoMenuProtectedRoutes.tsx";
import OrganizationOnboarding from "./app/onboarding/organization-onboarding/OrganizationOnboarding.tsx";
import SignIn from "./app/authorization/sign-in/SignIn.tsx";
import AccountRecovery from "./app/authorization/account-recovery/AccountRecovery.tsx";
import SignUp from "./app/authorization/sign-up/SignUp.tsx";
import ProtectedRoute from "./app/components/ProtectedRoutes.tsx";
import AppSessionExpired from "./app/app-session-expired/AppSessionExpired.tsx";
import {NotificationProvider} from "./context/NotificationContext.tsx";

const App: React.FC = () =>
{
    return (
        <BrowserRouter>
            <AuthProvider>
                <NotificationProvider>
                    <Routes>
                        {/*<Route path="/"*/}
                        {/*       element={*/}
                        {/*           <Home/>*/}
                        {/*       }/>*/}

                        <Route path="/sign-in"
                               element={
                                   <RedirectIfNotAuthenticated element={<SignIn/>}/>
                               }/>

                        <Route path="/sign-up"
                               element={
                                   <RedirectIfNotAuthenticated element={<SignUp/>
                                   }/>
                               }/>

                        <Route path="/account-recovery"
                               element={
                                   <RedirectIfNotAuthenticated
                                       element={<AccountRecovery/>}/>
                               }/>

                        <Route path="/nas"

                               element={
                                   <RedirectIfNotAuthenticated
                                       element={<NoAuthSharingSession/>
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

                        <Route path="/sharing-sessions"
                               element={
                                   <ProtectedRoute path='/sign-in'
                                                   element={<SharingSessions/>}/>
                               }/>

                        <Route path="*"
                               element={
                                   <NotFound/>
                               }/>
                    </Routes>
                </NotificationProvider>
            </AuthProvider>
        </BrowserRouter>
    );
};

export default App;