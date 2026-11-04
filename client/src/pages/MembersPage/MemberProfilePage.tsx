import { Navigate, useParams } from 'react-router-dom'
const MemberProfilePage = () => {
  const { userId } = useParams()
  return <Navigate to={`/team?member=${userId}`} replace />
}
export default MemberProfilePage
