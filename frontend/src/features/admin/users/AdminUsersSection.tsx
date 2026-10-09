import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import { PageTitle } from "@/components/common/PageTitle";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { listUsers, type UsersPage } from "./api";

const PAGE_SIZE = 20;
const numberFormat = new Intl.NumberFormat("es-AR");

export function AdminUsersSection() {
  const [page, setPage] = useState(0);
  const [usersPage, setUsersPage] = useState<UsersPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let cancelled = false;
    listUsers(page, PAGE_SIZE)
      .then((result) => {
        if (!cancelled) {
          setUsersPage(result);
          setError(null);
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          setError(apiErrorMessage(requestError, "No se pudieron cargar los usuarios."));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [page, retry]);

  function changePage(nextPage: number) {
    setLoading(true);
    setPage(nextPage);
  }

  function reload() {
    setLoading(true);
    setRetry((current) => current + 1);
  }

  const users = usersPage?.items ?? [];
  const total = usersPage?.totalElements ?? 0;
  const first = page * PAGE_SIZE + 1;
  const last = Math.min((page + 1) * PAGE_SIZE, total);

  return (
    <section>
      <PageTitle title="Usuarios" />

      {loading ? (
        <LoadingState label="Cargando usuarios…" />
      ) : error ? (
        <ErrorState title="No pudimos cargar los usuarios" description={error} onRetry={reload} />
      ) : users.length === 0 ? (
        <EmptyState title="Todavía no hay usuarios" description="Los usuarios registrados van a aparecer acá." />
      ) : (
        <Card>
          <CardContent>
            <div className="overflow-x-auto">
              <Table>
                <caption className="sr-only">Usuarios registrados en ScoreGrid</caption>
                <TableHeader>
                  <TableRow>
                    <TableHead>Usuario</TableHead>
                    <TableHead>Correo electrónico</TableHead>
                    <TableHead>Roles</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.id}>
                      <TableCell className="font-semibold">{user.username}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">{user.email}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1.5">
                          {user.roles.includes("ADMIN") && (
                            <Badge variant="finished">Administrador</Badge>
                          )}
                          {user.roles.includes("PLAYER") && (
                            <Badge variant="secondary">Participante</Badge>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      )}

      {!loading && !error && usersPage && usersPage.totalPages > 1 && (
        <nav aria-label="Páginas de usuarios" className="mt-4 flex items-center justify-between gap-3">
          <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => changePage(page - 1)}>
            Anterior
          </Button>
          <span className="text-center text-sm text-muted-foreground">
            {numberFormat.format(first)}–{numberFormat.format(last)} de {numberFormat.format(total)} usuarios
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={page + 1 >= usersPage.totalPages}
            onClick={() => changePage(page + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
    </section>
  );
}
