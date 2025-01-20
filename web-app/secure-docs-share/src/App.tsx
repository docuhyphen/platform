import React from 'react';
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import Home from './app/home';
import SignIn from './app/sign-in';
import IndividualRegistration from './app/individual-registration';
import CompanyRegistration from './app/company-registration';
import { AuthProvider } from './context/AuthContext';
import './App.css';
import ProtectedRoute from "./app/components/ProtectedRoutes.tsx";
import Landing from "./app/landing/Landing.tsx";
import SignUp from "./app/sign-up";
import RedirectIfAuthenticated from "./app/components/RedirectIfAuthenticated.tsx";

const App: React.FC = () => {
    return (
        <AuthProvider>
            <Router>
                <Routes>
                    <Route path="/" element={<Home />} />
                    <Route path="/sign-in" element={<RedirectIfAuthenticated element={<SignIn />} />} />
                    <Route path="/sign-up" element={<RedirectIfAuthenticated element={<SignUp />} />} />
                    <Route path="/individual-registration" element={<ProtectedRoute path='/sign-in' element={<IndividualRegistration />} />} />
                    <Route path="/company-registration" element={<ProtectedRoute path='/sign-in' element={<CompanyRegistration />} />} />
                    <Route path="/landing" element={<ProtectedRoute path='/sign-in' element={<Landing />} />} />
                </Routes>
            </Router>
        </AuthProvider>
    );
};

export default App;