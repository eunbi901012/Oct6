import { useLocation } from "react-router-dom";
import { screenContracts } from "../app/navigation";
import { Icon } from "./Icon";

export function PageHeading({ title }: { title: string }) {
  const { pathname } = useLocation();
  const contract = screenContracts[pathname];
  return (
    <div className="page-heading">
      <p className="eyebrow">
        <Icon name={contract?.icon ?? "menu"} /> 시스템 관리
      </p>
      <h1>{title}</h1>
      {contract && <p>{contract.description}</p>}
    </div>
  );
}
