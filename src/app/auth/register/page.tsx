import { redirect } from 'next/navigation';

export default function RegisterPage() {
  // Redirect to login page since registration is not available
  redirect('/auth/login');
}