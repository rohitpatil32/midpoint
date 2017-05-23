package com.evolveum.midpoint.web.component.objectdetails;

import com.evolveum.midpoint.gui.api.component.ObjectBrowserPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.InOidFilter;
import com.evolveum.midpoint.prism.query.NotFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.assignment.AssignmentEditorDto;
import com.evolveum.midpoint.web.component.assignment.AssignmentTablePanel;
import com.evolveum.midpoint.web.component.assignment.DelegationEditorPanel;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.prism.ObjectWrapper;
import com.evolveum.midpoint.web.page.admin.users.component.AssignmentsPreviewDto;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by honchar
 */
public class UserDelegationsTabPanel<F extends FocusType> extends AbstractObjectTabPanel {
    private static final long serialVersionUID = 1L;

    private static final String ID_DELEGATIONS_CONTAINER = "delegationsContainer";
    private static final String ID_DELEGATIONS_PANEL = "delegationsPanel";

    private static final Trace LOGGER = TraceManager.getTrace(FocusAssignmentsTabPanel.class);

    private LoadableModel<List<AssignmentEditorDto>> delegationsModel;
    private List<AssignmentsPreviewDto> privilegesList = new ArrayList<>();

    public UserDelegationsTabPanel(String id, Form mainForm, LoadableModel<ObjectWrapper<F>> focusWrapperModel,
                                    LoadableModel<List<AssignmentEditorDto>> delegationsModel,
                                   List<AssignmentsPreviewDto> privilegesList, PageBase page) {
        super(id, mainForm, focusWrapperModel, page);
        this.delegationsModel = delegationsModel;
        this.privilegesList = privilegesList;
        initLayout();
    }

    private void initLayout() {

        WebMarkupContainer delegations = new WebMarkupContainer(ID_DELEGATIONS_CONTAINER);
        delegations.setOutputMarkupId(true);
        add(delegations);

        AssignmentTablePanel panel = new AssignmentTablePanel<UserType>(ID_DELEGATIONS_PANEL, createStringResource("FocusType.delegations"),
                delegationsModel) {
            private static final long serialVersionUID = 1L;

            @Override
            public void populateAssignmentDetailsPanel(ListItem<AssignmentEditorDto> item) {
                DelegationEditorPanel editor = new DelegationEditorPanel(ID_ROW, item.getModel(), false,
                        privilegesList, pageBase);
                item.add(editor);
            }

            @Override
            public String getExcludeOid() {
                return getObjectWrapper().getOid();
            }

            @Override
            protected List<InlineMenuItem> createAssignmentMenu() {
                List<InlineMenuItem> items = new ArrayList<>();

                InlineMenuItem item;
                if (WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_DELEGATE_ACTION_URL)) {
                    item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.addDelegation"),
                            new InlineMenuItemAction() {
                                private static final long serialVersionUID = 1L;

                                @Override
                                public void onClick(AjaxRequestTarget target) {
                                    List<QName> supportedTypes = new ArrayList<>();
                                    supportedTypes.add(UserType.COMPLEX_TYPE);
                                    ObjectFilter filter = InOidFilter.createInOid(getObjectWrapper().getOid());
                                    ObjectFilter notFilter = NotFilter.createNot(filter);
                                    ObjectBrowserPanel<UserType> panel = new ObjectBrowserPanel<UserType>(
                                            pageBase.getMainPopupBodyId(), UserType.class,
                                            supportedTypes, false, pageBase, notFilter) {
                                        private static final long serialVersionUID = 1L;

                                        @Override
                                        protected void onSelectPerformed(AjaxRequestTarget target, UserType user) {
                                            pageBase.hideMainPopup(target);
                                            List<ObjectType> newAssignmentsList = new ArrayList<ObjectType>();
                                            newAssignmentsList.add(user);
                                            addSelectedAssignablePerformed(target, newAssignmentsList, getPageBase().getMainPopup().getId());
                                        }

                                    };
                                    panel.setOutputMarkupId(true);
                                    pageBase.showMainPopup(panel, target);

                                }
                            });
                    items.add(item);
                }
                if (WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_UNASSIGN_ACTION_URL)) {
                    item = new InlineMenuItem(createStringResource("AssignmentTablePanel.menu.deleteDelegation"),
                            new InlineMenuItemAction() {
                                private static final long serialVersionUID = 1L;

                                @Override
                                public void onClick(AjaxRequestTarget target) {
                                    deleteAssignmentPerformed(target);
                                }
                            });
                    items.add(item);
                }

                return items;
            }

            @Override
            protected String getNoAssignmentsSelectedMessage() {
                return getString("AssignmentTablePanel.message.noDelegationsSelected");
            }

            @Override
            protected String getAssignmentsDeleteMessage(int size) {
                return createStringResource("AssignmentTablePanel.modal.message.deleteDelegation",
                        size).getString();
            }

            @Override
            protected void addSelectedAssignablePerformed(AjaxRequestTarget target, List<ObjectType> newAssignments,
                                                          String popupId) {
                ModalWindow window = (ModalWindow) get(popupId);
                if (window != null) {
                    window.close(target);
                }
                getPageBase().hideMainPopup(target);
                if (newAssignments.isEmpty()) {
                    warn(getString("AssignmentTablePanel.message.noAssignmentSelected"));
                    target.add(getPageBase().getFeedbackPanel());
                    return;
                }

                for (ObjectType object : newAssignments) {
                    try {
                        AssignmentEditorDto dto = AssignmentEditorDto.createDtoAddFromSelectedObject(
                                ((PrismObject<UserType>)getObjectWrapper().getObject()).asObjectable(),
                                SchemaConstants.ORG_DEPUTY, getPageBase(), (UserType) object);
                        dto.setPrivilegeLimitationList(privilegesList);
                        delegationsModel.getObject().add(dto);
                    } catch (Exception e) {
                        error(getString("AssignmentTablePanel.message.couldntAssignObject", object.getName(),
                                e.getMessage()));
                        LoggingUtils.logUnexpectedException(LOGGER, "Couldn't assign object", e);
                    }
                }
                reloadAssignmentsPanel(target);
                reloadMainFormButtons(target);
            }
        };

        delegations.add(panel);
    }

    public boolean isDelegationsModelChanged(){
        return getDelegationsTablePanel().isModelChanged();
    }

    private AssignmentTablePanel getDelegationsTablePanel(){
        return (AssignmentTablePanel) get(ID_DELEGATIONS_CONTAINER).get(ID_DELEGATIONS_PANEL);
    }

}